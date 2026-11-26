package com.bankplatform.transaction;

import com.bankplatform.common.event.CurrencyCode;
import com.bankplatform.common.event.EventJson;
import com.bankplatform.common.event.RejectionReason;
import com.bankplatform.common.event.TransactionCreatedEvent;
import com.bankplatform.common.event.TransactionEvent;
import com.bankplatform.common.event.TransactionRejectedEvent;
import com.bankplatform.common.event.TransactionStatus;
import com.bankplatform.common.kafka.KafkaEventPublisher;
import com.bankplatform.common.kafka.KafkaTopics;
import com.bankplatform.common.logging.CorrelationId;
import com.bankplatform.transaction.account.dto.AccountResponse;
import com.bankplatform.transaction.account.dto.BalanceResponse;
import com.bankplatform.transaction.account.dto.CreateAccountRequest;
import com.bankplatform.transaction.auth.dto.LoginRequest;
import com.bankplatform.transaction.auth.dto.RegisterRequest;
import com.bankplatform.transaction.auth.dto.TokenResponse;
import com.bankplatform.transaction.outbox.OutboxRepository;
import com.bankplatform.transaction.support.TestcontainersConfiguration;
import com.bankplatform.transaction.transfer.dto.CreateTransferRequest;
import com.bankplatform.transaction.transfer.dto.TransactionResponse;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.kafka.KafkaConnectionDetails;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "app.security.jwt.secret=integration-test-secret-integration-test-secret",
        "app.outbox.poll-interval=200ms"
})
@Import(TestcontainersConfiguration.class)
class TransactionApiIT {

    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private KafkaConnectionDetails kafkaConnection;
    @Autowired
    private KafkaEventPublisher kafkaPublisher;
    @Autowired
    private OutboxRepository outboxRepository;

    @Test
    void transferIsAcceptedIdempotentPublishedViaOutboxAndFinalizedByResultEvent() {
        String aliceToken = registerAndLogin("alice-" + suffix());
        String bobToken = registerAndLogin("bob-" + suffix());
        AccountResponse aliceAccount = openAccount(aliceToken, "1000.00");
        AccountResponse bobAccount = openAccount(bobToken, null);

        var request = new CreateTransferRequest(aliceAccount.number(), bobAccount.number(),
                new BigDecimal("250.00"), CurrencyCode.AZN, "Dinner");
        String key = UUID.randomUUID().toString();
        String correlationId = "it-" + suffix();

        ResponseEntity<TransactionResponse> first = transfer(aliceToken, key, correlationId, request);
        ResponseEntity<TransactionResponse> replay = transfer(aliceToken, key, correlationId, request);

        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(first.getBody().status()).isEqualTo(TransactionStatus.PENDING);
        assertThat(first.getHeaders().getLocation()).isNotNull();
        assertThat(replay.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(replay.getHeaders().getFirst("Idempotent-Replayed")).isEqualTo("true");
        assertThat(replay.getBody().id()).isEqualTo(first.getBody().id());

        UUID txId = first.getBody().id();

        // the outbox relays exactly one event, carrying the HTTP correlation id as a Kafka header
        ConsumerRecord<String, String> record = awaitCreatedEvent(txId);
        TransactionEvent event = EventJson.read(record.value());
        assertThat(event).isInstanceOf(TransactionCreatedEvent.class);
        assertThat(record.key()).isEqualTo(aliceAccount.number());
        assertThat(new String(record.headers().lastHeader(CorrelationId.HEADER).value(), StandardCharsets.UTF_8))
                .isEqualTo(correlationId);
        await().atMost(Duration.ofSeconds(10)).until(() -> outboxRepository.countByPublishedAtIsNull() == 0);

        // processing-service's verdict arrives on Kafka -> the status is updated
        kafkaPublisher.publish(new TransactionRejectedEvent(UUID.randomUUID(), txId, aliceAccount.number(),
                bobAccount.number(), new BigDecimal("250.00"), CurrencyCode.AZN, null, null,
                RejectionReason.DAILY_LIMIT_EXCEEDED, "limit", Instant.now()));

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            TransactionResponse current = get(aliceToken, "/api/v1/transactions/" + txId, TransactionResponse.class).getBody();
            assertThat(current.status()).isEqualTo(TransactionStatus.REJECTED);
            assertThat(current.rejectionReason()).isEqualTo(RejectionReason.DAILY_LIMIT_EXCEEDED);
        });
    }

    @Test
    void balanceIsOnlyVisibleToOwner() {
        String aliceToken = registerAndLogin("carol-" + suffix());
        String eveToken = registerAndLogin("eve-" + suffix());
        AccountResponse account = openAccount(aliceToken, "42.50");

        var own = get(aliceToken, "/api/v1/accounts/" + account.number() + "/balance", BalanceResponse.class);
        var foreign = get(eveToken, "/api/v1/accounts/" + account.number() + "/balance", Map.class);

        assertThat(own.getBody().balance()).isEqualByComparingTo("42.50");
        assertThat(foreign.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void errorsAreRfc7807ProblemDetails() {
        String token = registerAndLogin("dave-" + suffix());
        var body = new CreateTransferRequest("A", "B", new BigDecimal("1.00"), CurrencyCode.AZN, null);

        HttpHeaders headers = jsonHeaders(token);
        ResponseEntity<Map<String, Object>> missingKey = rest.exchange("/api/v1/transactions", HttpMethod.POST,
                new HttpEntity<>(body, headers), new ParameterizedTypeReference<>() {});
        ResponseEntity<Map<String, Object>> unauthenticated = rest.exchange("/api/v1/accounts", HttpMethod.GET,
                HttpEntity.EMPTY, new ParameterizedTypeReference<>() {});
        ResponseEntity<Map<String, Object>> invalidBody = rest.exchange("/api/v1/auth/register", HttpMethod.POST,
                new HttpEntity<>(Map.of("username", "x"), jsonHeaders(null)), new ParameterizedTypeReference<>() {});

        assertThat(missingKey.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(missingKey.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(missingKey.getBody()).containsKeys("type", "title", "status", "detail", "correlationId");

        assertThat(unauthenticated.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(unauthenticated.getBody()).containsEntry("errorCode", "unauthorized");

        assertThat(invalidBody.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(invalidBody.getBody()).containsEntry("errorCode", "validation-error").containsKey("errors");
    }

    private ConsumerRecord<String, String> awaitCreatedEvent(UUID txId) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, String.join(",", kafkaConnection.getBootstrapServers()));
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "it-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        List<ConsumerRecord<String, String>> matches = new ArrayList<>();
        try (var consumer = new KafkaConsumer<>(props, new StringDeserializer(), new StringDeserializer())) {
            consumer.subscribe(List.of(KafkaTopics.TRANSACTIONS_CREATED));
            await().atMost(Duration.ofSeconds(20)).until(() -> {
                consumer.poll(Duration.ofMillis(500)).forEach(r -> {
                    if (r.value().contains(txId.toString())) {
                        matches.add(r);
                    }
                });
                return !matches.isEmpty();
            });
        }
        assertThat(matches).hasSize(1);
        return matches.getFirst();
    }

    private String registerAndLogin(String username) {
        rest.postForEntity("/api/v1/auth/register",
                new RegisterRequest(username, "Password123!", "Test User"), Map.class);
        return rest.postForEntity("/api/v1/auth/login", new LoginRequest(username, "Password123!"),
                TokenResponse.class).getBody().accessToken();
    }

    private AccountResponse openAccount(String token, String deposit) {
        var request = new CreateAccountRequest(CurrencyCode.AZN, deposit == null ? null : new BigDecimal(deposit));
        var response = rest.exchange("/api/v1/accounts", HttpMethod.POST, new HttpEntity<>(request, jsonHeaders(token)),
                AccountResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody();
    }

    private ResponseEntity<TransactionResponse> transfer(String token, String key, String correlationId,
                                                         CreateTransferRequest request) {
        HttpHeaders headers = jsonHeaders(token);
        headers.set("Idempotency-Key", key);
        headers.set(CorrelationId.HEADER, correlationId);
        return rest.exchange("/api/v1/transactions", HttpMethod.POST, new HttpEntity<>(request, headers),
                TransactionResponse.class);
    }

    private <T> ResponseEntity<T> get(String token, String url, Class<T> type) {
        return rest.exchange(url, HttpMethod.GET, new HttpEntity<>(jsonHeaders(token)), type);
    }

    private static HttpHeaders jsonHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            headers.setBearerAuth(token);
        }
        return headers;
    }

    private static String suffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
