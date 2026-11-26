package com.bankplatform.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.lifecycle.Startables;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * The whole platform, end to end: three real service processes, real Postgres, Kafka and
 * Elasticsearch. Flow: login -> transfer -> processed by processing-service -> status updated in
 * transaction-service -> searchable in search-service; plus a transfer rejected for insufficient funds.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TransferFlowE2EIT {

    private static final String JWT_SECRET = "e2e-secret-e2e-secret-e2e-secret-e2e-secret";
    private static final Duration STARTUP = Duration.ofMinutes(3);

    private final Network network = Network.newNetwork();
    private final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:17-alpine"))
            .withNetwork(network);
    private final KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("apache/kafka:3.9.1"))
            .withNetwork(network);
    private final ElasticsearchContainer elasticsearch = new ElasticsearchContainer(
            DockerImageName.parse("docker.elastic.co/elasticsearch/elasticsearch:8.18.8"))
            .withEnv("xpack.security.enabled", "false")
            .withEnv("ES_JAVA_OPTS", "-Xms512m -Xmx512m")
            .waitingFor(Wait.forHttp("/_cluster/health?wait_for_status=yellow").forPort(9200)
                    .withStartupTimeout(STARTUP))
            .withNetwork(network);

    private final Api api = new Api();
    private ServiceProcess transactionService;
    private ServiceProcess processingService;
    private ServiceProcess searchService;

    @BeforeAll
    void startPlatform() throws Exception {
        Startables.deepStart(postgres, kafka, elasticsearch).join();

        Map<String, String> env = new HashMap<>();
        env.put("DB_URL", postgres.getJdbcUrl());
        env.put("DB_USERNAME", postgres.getUsername());
        env.put("DB_PASSWORD", postgres.getPassword());
        env.put("KAFKA_BOOTSTRAP_SERVERS", kafka.getBootstrapServers());
        env.put("ELASTICSEARCH_URIS", "http://" + elasticsearch.getHttpHostAddress());
        env.put("JWT_SECRET", JWT_SECRET);
        env.put("SPRING_PROFILES_ACTIVE", "dev");

        // transaction-service owns the schema (Flyway) and seeds demo data -> must be up first
        transactionService = ServiceProcess.start("transaction-service", env);
        transactionService.awaitHealthy(STARTUP);
        processingService = ServiceProcess.start("processing-service", env);
        searchService = ServiceProcess.start("search-service", env);
        processingService.awaitHealthy(STARTUP);
        searchService.awaitHealthy(STARTUP);
    }

    @AfterAll
    void stopPlatform() {
        for (ServiceProcess service : new ServiceProcess[]{searchService, processingService, transactionService}) {
            if (service != null) {
                service.close();
            }
        }
        elasticsearch.stop();
        kafka.stop();
        postgres.stop();
        network.close();
    }

    @Test
    void transferIsProcessedAndBecomesSearchable() throws Exception {
        String alice = login("alice", "Customer123!");
        String bob = login("bob", "Customer123!");
        String admin = login("admin", "Admin123!");
        String aliceAzn = accountNumber(alice, "AZN");
        String bobAzn = accountNumber(bob, "AZN");
        BigDecimal aliceBefore = balance(alice, aliceAzn);
        String correlationId = "e2e-" + UUID.randomUUID();

        // 1. create transfer
        Api.Response created = api.post(tx("/api/v1/transactions"), alice, Map.of(
                "sourceAccount", aliceAzn, "targetAccount", bobAzn,
                "amount", "100.00", "currency", "AZN", "description", "E2E coffee money"),
                Map.of("Idempotency-Key", UUID.randomUUID().toString(), "X-Correlation-Id", correlationId));
        assertThat(created.status()).isEqualTo(202);
        assertThat(created.body().path("status").asText()).isEqualTo("PENDING");
        String txId = created.body().path("id").asText();

        // 2. processed: status COMPLETED and money moved
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(api.get(tx("/api/v1/transactions/" + txId), alice).body().path("status").asText())
                        .isEqualTo("COMPLETED"));
        assertThat(balance(alice, aliceAzn)).isEqualByComparingTo(aliceBefore.subtract(new BigDecimal("100.00")));

        // 3. searchable
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            JsonNode hits = api.get(search("/api/v1/search/transactions?q=coffee&status=COMPLETED"), bob).body();
            assertThat(hits.path("content").findValuesAsText("id")).contains(txId);
        });
        JsonNode stats = api.get(search("/api/v1/search/transactions/stats"), admin).body();
        assertThat(stats.findValuesAsText("status")).contains("COMPLETED");

        // 4. the HTTP correlation id travelled through Kafka into the other services' logs
        assertThat(processingService.logs()).contains(correlationId);
        assertThat(searchService.logs()).doesNotContain("ERROR");
    }

    @Test
    void transferWithInsufficientFundsIsRejectedAndSearchable() throws Exception {
        String bob = login("bob", "Customer123!");
        String alice = login("alice", "Customer123!");
        String bobAzn = accountNumber(bob, "AZN");
        String aliceAzn = accountNumber(alice, "AZN");
        BigDecimal bobBefore = balance(bob, bobAzn);

        Api.Response created = api.post(tx("/api/v1/transactions"), bob, Map.of(
                "sourceAccount", bobAzn, "targetAccount", aliceAzn,
                "amount", "9999.00", "currency", "AZN", "description", "Too expensive"),
                Map.of("Idempotency-Key", UUID.randomUUID().toString()));
        String txId = created.body().path("id").asText();

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            JsonNode tx = api.get(tx("/api/v1/transactions/" + txId), bob).body();
            assertThat(tx.path("status").asText()).isEqualTo("REJECTED");
            assertThat(tx.path("rejectionReason").asText()).isEqualTo("INSUFFICIENT_FUNDS");
        });
        assertThat(balance(bob, bobAzn)).isEqualByComparingTo(bobBefore);

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            JsonNode hits = api.get(search("/api/v1/search/transactions?status=REJECTED&account=" + bobAzn), bob).body();
            assertThat(hits.path("content").findValuesAsText("id")).contains(txId);
        });
    }

    private String login(String username, String password) throws Exception {
        Api.Response response = api.post(tx("/api/v1/auth/login"), null,
                Map.of("username", username, "password", password), Map.of());
        assertThat(response.status()).as("login %s", username).isEqualTo(200);
        return response.body().path("accessToken").asText();
    }

    private String accountNumber(String token, String currency) throws Exception {
        for (JsonNode account : api.get(tx("/api/v1/accounts"), token).body()) {
            if (account.path("currency").asText().equals(currency)) {
                return account.path("number").asText();
            }
        }
        throw new AssertionError("No " + currency + " account");
    }

    private BigDecimal balance(String token, String number) throws Exception {
        return api.get(tx("/api/v1/accounts/" + number + "/balance"), token).body().path("balance").decimalValue();
    }

    private String tx(String path) {
        return transactionService.baseUrl() + path;
    }

    private String search(String path) {
        return searchService.baseUrl() + path;
    }
}
