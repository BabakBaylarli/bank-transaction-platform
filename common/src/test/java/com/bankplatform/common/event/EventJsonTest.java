package com.bankplatform.common.event;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EventJsonTest {

    private static final Instant NOW = Instant.parse("2026-01-15T10:15:30Z");

    @Test
    void roundTripsEverySubtypeAndKeepsTypeInformation() {
        UUID txId = UUID.randomUUID();
        TransactionEvent created = new TransactionCreatedEvent(UUID.randomUUID(), txId, "AZ1", "AZ2",
                new BigDecimal("125.50"), CurrencyCode.AZN, UUID.randomUUID(), UUID.randomUUID(), "rent", NOW);
        TransactionEvent rejected = new TransactionRejectedEvent(UUID.randomUUID(), txId, "AZ1", "AZ2",
                new BigDecimal("125.50"), CurrencyCode.AZN, null, null,
                RejectionReason.INSUFFICIENT_FUNDS, "balance too low", NOW);

        assertThat(EventJson.read(EventJson.write(created))).isEqualTo(created);
        assertThat(EventJson.read(EventJson.write(rejected))).isEqualTo(rejected);
        assertThat(EventJson.write(created)).contains("\"type\":\"TransactionCreated\"");
    }

    @Test
    void keepsMoneyPrecision() {
        var event = new TransactionCompletedEvent(UUID.randomUUID(), UUID.randomUUID(), "AZ1", "AZ2",
                new BigDecimal("0.10"), CurrencyCode.USD, null, null, NOW);

        var parsed = (TransactionCompletedEvent) EventJson.read(EventJson.write(event));

        assertThat(parsed.amount()).isEqualByComparingTo("0.10");
    }

    @Test
    void rejectsMalformedPayloadAsPoisonMessage() {
        assertThatThrownBy(() -> EventJson.read("{not json"))
                .isInstanceOf(InvalidEventException.class);
        assertThatThrownBy(() -> EventJson.read("{\"type\":\"Unknown\"}"))
                .isInstanceOf(InvalidEventException.class);
    }

    @Test
    void exhaustiveSwitchOverSealedHierarchy() {
        TransactionEvent event = new TransactionCompletedEvent(UUID.randomUUID(), UUID.randomUUID(), "A", "B",
                BigDecimal.ONE, CurrencyCode.EUR, null, null, NOW);

        TransactionStatus status = switch (event) {
            case TransactionCreatedEvent e -> TransactionStatus.PENDING;
            case TransactionCompletedEvent e -> TransactionStatus.COMPLETED;
            case TransactionRejectedEvent e -> TransactionStatus.REJECTED;
        };

        assertThat(status).isEqualTo(TransactionStatus.COMPLETED);
    }
}
