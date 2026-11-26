package com.bankplatform.search;

import com.bankplatform.common.event.CurrencyCode;
import com.bankplatform.common.event.TransactionCompletedEvent;
import com.bankplatform.common.event.TransactionCreatedEvent;
import com.bankplatform.common.event.TransactionRejectedEvent;
import com.bankplatform.common.event.RejectionReason;
import com.bankplatform.common.kafka.KafkaEventPublisher;
import com.bankplatform.common.security.AuthenticatedUser;
import com.bankplatform.common.security.JwtTokenService;
import com.bankplatform.common.security.Role;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "app.security.jwt.secret=integration-test-secret-integration-test-secret")
@Import(SearchIT.Containers.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SearchIT {

    @TestConfiguration(proxyBeanMethods = false)
    static class Containers {
        @Bean
        @ServiceConnection
        ElasticsearchContainer elasticsearch() {
            return new ElasticsearchContainer(
                    DockerImageName.parse("docker.elastic.co/elasticsearch/elasticsearch:8.18.8"))
                    .withEnv("xpack.security.enabled", "false")
                    .withEnv("ES_JAVA_OPTS", "-Xms512m -Xmx512m")
                    .waitingFor(Wait.forHttp("/_cluster/health?wait_for_status=yellow").forPort(9200)
                            .withStartupTimeout(Duration.ofMinutes(3)));
        }

        @Bean
        @ServiceConnection
        KafkaContainer kafka() {
            return new KafkaContainer(DockerImageName.parse("apache/kafka:3.9.1"));
        }
    }

    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private KafkaEventPublisher publisher;
    @Autowired
    private JwtTokenService tokens;

    private final UUID alice = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();
    private final UUID mallory = UUID.randomUUID();
    private final String aliceAccount = "AZ00ALICE" + UUID.randomUUID().toString().substring(0, 8);
    private final String bobAccount = "AZ00BOB" + UUID.randomUUID().toString().substring(0, 8);
    private UUID completedTx;
    private UUID rejectedTx;

    @BeforeAll
    void publishEvents() {
        Instant now = Instant.now();
        completedTx = UUID.randomUUID();
        rejectedTx = UUID.randomUUID();
        // completed arrives BEFORE created - the read model must cope with any order
        publisher.publish(new TransactionCompletedEvent(UUID.randomUUID(), completedTx, aliceAccount, bobAccount,
                new BigDecimal("150.00"), CurrencyCode.AZN, alice, bob, now.plusSeconds(1)));
        publisher.publish(new TransactionCreatedEvent(UUID.randomUUID(), completedTx, aliceAccount, bobAccount,
                new BigDecimal("150.00"), CurrencyCode.AZN, alice, bob, "Monthly rent payment", now));
        publisher.publish(new TransactionCreatedEvent(UUID.randomUUID(), rejectedTx, aliceAccount, bobAccount,
                new BigDecimal("9000.00"), CurrencyCode.AZN, alice, bob, "New laptop", now));
        publisher.publish(new TransactionRejectedEvent(UUID.randomUUID(), rejectedTx, aliceAccount, bobAccount,
                new BigDecimal("9000.00"), CurrencyCode.AZN, alice, bob, RejectionReason.INSUFFICIENT_FUNDS,
                "Insufficient funds", now.plusSeconds(1)));

        await().atMost(Duration.ofSeconds(60)).untilAsserted(() ->
                assertThat(search(alice, Role.CUSTOMER, "account=" + aliceAccount).path("totalElements").asInt())
                        .isEqualTo(2));
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(search(alice, Role.CUSTOMER, "account=" + aliceAccount + "&status=COMPLETED")
                        .path("content").path(0).path("description").asText()).isEqualTo("Monthly rent payment"));
    }

    @Test
    void filtersByStatusAmountAndFreeText() {
        JsonNode rejected = search(alice, Role.CUSTOMER, "account=" + aliceAccount + "&status=REJECTED");
        JsonNode bigOnes = search(alice, Role.CUSTOMER, "account=" + aliceAccount + "&minAmount=1000");
        JsonNode rent = search(alice, Role.CUSTOMER, "q=rent&account=" + aliceAccount);

        assertThat(rejected.path("content").path(0).path("id").asText()).isEqualTo(rejectedTx.toString());
        assertThat(rejected.path("content").path(0).path("rejectionReason").asText()).isEqualTo("INSUFFICIENT_FUNDS");
        assertThat(bigOnes.path("totalElements").asInt()).isEqualTo(1);
        assertThat(bigOnes.path("content").path(0).path("amount").decimalValue()).isEqualByComparingTo("9000.00");
        assertThat(rent.path("totalElements").asInt()).isEqualTo(1);
        assertThat(rent.path("content").path(0).path("status").asText()).isEqualTo("COMPLETED");
    }

    @Test
    void customersOnlySeeTheirOwnTransactions() {
        assertThat(search(bob, Role.CUSTOMER, "account=" + aliceAccount).path("totalElements").asInt()).isEqualTo(2);
        assertThat(search(mallory, Role.CUSTOMER, "account=" + aliceAccount).path("totalElements").asInt()).isZero();
        assertThat(search(mallory, Role.ADMIN, "account=" + aliceAccount).path("totalElements").asInt()).isEqualTo(2);
    }

    @Test
    void statsAreAdminOnly() {
        ResponseEntity<JsonNode> customer = get(alice, Role.CUSTOMER, "/api/v1/search/transactions/stats");
        ResponseEntity<JsonNode> admin = get(mallory, Role.ADMIN, "/api/v1/search/transactions/stats");

        assertThat(customer.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(customer.getBody().path("type").asText()).endsWith("/forbidden");
        assertThat(admin.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(admin.getBody().isArray()).isTrue();
        assertThat(admin.getBody().findValuesAsText("status")).contains("COMPLETED", "REJECTED");
    }

    @Test
    void invalidRangeIsRejected() {
        ResponseEntity<JsonNode> response = get(alice, Role.CUSTOMER,
                "/api/v1/search/transactions?minAmount=100&maxAmount=10");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().path("errorCode").asText()).isEqualTo("validation-error");
    }

    private JsonNode search(UUID user, Role role, String query) {
        ResponseEntity<JsonNode> response = get(user, role, "/api/v1/search/transactions?" + query);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private ResponseEntity<JsonNode> get(UUID user, Role role, String url) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(tokens.issue(new AuthenticatedUser(user, "user-" + user, role)).token());
        return rest.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), JsonNode.class);
    }
}
