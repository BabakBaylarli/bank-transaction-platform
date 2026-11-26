package com.bankplatform.processing;

import com.bankplatform.common.event.CurrencyCode;
import com.bankplatform.common.event.EventJson;
import com.bankplatform.common.event.RejectionReason;
import com.bankplatform.common.event.TransactionCompletedEvent;
import com.bankplatform.common.event.TransactionCreatedEvent;
import com.bankplatform.common.event.TransactionEvent;
import com.bankplatform.common.event.TransactionRejectedEvent;
import com.bankplatform.common.kafka.KafkaEventPublisher;
import com.bankplatform.common.kafka.KafkaTopics;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.kafka.KafkaConnectionDetails;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(properties = {
        "app.security.jwt.secret=integration-test-secret-integration-test-secret",
        "spring.flyway.locations=classpath:db/migration,classpath:db/test-migration",
        "app.kafka.retry.initial-interval=100ms",
        "app.kafka.retry.max-attempts=1"
})
@Import(ProcessingIT.Containers.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ProcessingIT {

    @TestConfiguration(proxyBeanMethods = false)
    static class Containers {
        @Bean
        @ServiceConnection
        PostgreSQLContainer<?> postgres() {
            return new PostgreSQLContainer<>(DockerImageName.parse("postgres:17-alpine"));
        }

        @Bean
        @ServiceConnection
        KafkaContainer kafka() {
            return new KafkaContainer(DockerImageName.parse("apache/kafka:3.9.1"));
        }
    }

    @Autowired
    private KafkaEventPublisher publisher;
    @Autowired
    private KafkaConnectionDetails kafka;
    @Autowired
    private JdbcTemplate jdbc;

    private final Map<String, ConsumerRecord<String, String>> received = new ConcurrentHashMap<>();
    private volatile boolean polling = true;
    private Thread pollingThread;

    @BeforeAll
    void startResultConsumer() {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, String.join(",", kafka.getBootstrapServers()));
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "processing-it");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        pollingThread = Thread.ofVirtual().start(() -> {
            try (var consumer = new KafkaConsumer<>(props, new StringDeserializer(), new StringDeserializer())) {
                consumer.subscribe(List.of(KafkaTopics.TRANSACTIONS_COMPLETED, KafkaTopics.TRANSACTIONS_REJECTED,
                        KafkaTopics.TRANSACTIONS_CREATED_DLT));
                while (polling) {
                    consumer.poll(Duration.ofMillis(200)).forEach(r -> received.put(r.topic() + "|" + r.value(), r));
                }
            }
        });
    }

    @AfterAll
    void stopConsumer() throws InterruptedException {
        polling = false;
        pollingThread.join(5000);
    }

    @Test
    void completesValidTransferAndMovesBalancesAtomically() {
        String source = account("500.00", CurrencyCode.AZN);
        String target = account("0.00", CurrencyCode.AZN);
        TransactionCreatedEvent transfer = transfer(source, target, "120.25", CurrencyCode.AZN);

        publisher.publish(transfer);

        TransactionEvent result = awaitResultFor(transfer.transactionId());
        assertThat(result).isInstanceOf(TransactionCompletedEvent.class);
        assertThat(balance(source)).isEqualByComparingTo("379.75");
        assertThat(balance(target)).isEqualByComparingTo("120.25");
    }

    @Test
    void rejectsTransferAboveSingleAmountLimit() {
        String source = account("100000.00", CurrencyCode.USD);
        String target = account("0.00", CurrencyCode.USD);
        TransactionCreatedEvent transfer = transfer(source, target, "10000.01", CurrencyCode.USD);

        publisher.publish(transfer);

        assertThat(awaitResultFor(transfer.transactionId()))
                .isInstanceOfSatisfying(TransactionRejectedEvent.class,
                        r -> assertThat(r.reason()).isEqualTo(RejectionReason.AMOUNT_LIMIT_EXCEEDED));
        assertThat(balance(source)).isEqualByComparingTo("100000.00");
    }

    @Test
    void poisonMessageGoesToDeadLetterTopicWithCorrelationId() {
        String poison = "{\"this is\": \"not an event\" " + UUID.randomUUID();

        publisher.publish(KafkaTopics.TRANSACTIONS_CREATED, "key", "Garbage", poison, "corr-poison");

        await().atMost(Duration.ofSeconds(30))
                .until(() -> received.containsKey(KafkaTopics.TRANSACTIONS_CREATED_DLT + "|" + poison));
        ConsumerRecord<String, String> dlt = received.get(KafkaTopics.TRANSACTIONS_CREATED_DLT + "|" + poison);
        assertThat(new String(dlt.headers().lastHeader("X-Correlation-Id").value(), StandardCharsets.UTF_8))
                .isEqualTo("corr-poison");
        assertThat(dlt.headers().lastHeader("kafka_dlt-exception-fqcn")).isNotNull();
    }

    private TransactionEvent awaitResultFor(UUID transactionId) {
        return await().atMost(Duration.ofSeconds(30)).until(() -> received.values().stream()
                        .filter(r -> !r.topic().endsWith(".DLT"))
                        .map(r -> EventJson.read(r.value()))
                        .filter(e -> e.transactionId().equals(transactionId))
                        .findFirst().orElse(null),
                e -> e != null);
    }

    private String account(String balance, CurrencyCode currency) {
        String number = "AZ00TEST" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        jdbc.update("INSERT INTO public.accounts (id, number, owner_id, currency, balance, version, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, 0, ?, ?)", UUID.randomUUID(), number, UUID.randomUUID(), currency.name(),
                new BigDecimal(balance), Timestamp.from(Instant.now()), Timestamp.from(Instant.now()));
        return number;
    }

    private BigDecimal balance(String number) {
        return jdbc.queryForObject("SELECT balance FROM public.accounts WHERE number = ?", BigDecimal.class, number);
    }

    private static TransactionCreatedEvent transfer(String source, String target, String amount, CurrencyCode currency) {
        return new TransactionCreatedEvent(UUID.randomUUID(), UUID.randomUUID(), source, target, new BigDecimal(amount),
                currency, UUID.randomUUID(), UUID.randomUUID(), "it", Instant.now());
    }
}
