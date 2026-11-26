package com.bankplatform.search.index;

import com.bankplatform.common.event.CurrencyCode;
import com.bankplatform.common.event.RejectionReason;
import com.bankplatform.common.event.TransactionCompletedEvent;
import com.bankplatform.common.event.TransactionCreatedEvent;
import com.bankplatform.common.event.TransactionRejectedEvent;
import com.bankplatform.common.event.TransactionStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TransactionDocumentTest {

    private static final Instant T0 = Instant.parse("2026-05-10T10:00:00Z");
    private static final Instant T1 = T0.plusSeconds(2);

    private final UUID txId = UUID.randomUUID();
    private final UUID alice = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();

    @Test
    void createdThenCompleted() {
        var doc = TransactionDocument.empty(txId).apply(created()).apply(completed());

        assertThat(doc.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
        assertThat(doc.getDescription()).isEqualTo("rent");
        assertThat(doc.getCreatedAt()).isEqualTo(T0);
        assertThat(doc.getUpdatedAt()).isEqualTo(T1);
        assertThat(doc.getParticipants()).containsExactly(alice.toString(), bob.toString());
    }

    @Test
    void completedArrivingBeforeCreatedKeepsFinalStatus() {
        var doc = TransactionDocument.empty(txId).apply(completed()).apply(created());

        assertThat(doc.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
        assertThat(doc.getDescription()).isEqualTo("rent");
        assertThat(doc.getCreatedAt()).isEqualTo(T0);
        assertThat(doc.getUpdatedAt()).isEqualTo(T1);
    }

    @Test
    void firstFinalDecisionWins() {
        var doc = TransactionDocument.empty(txId).apply(created()).apply(rejected()).apply(completed());

        assertThat(doc.getStatus()).isEqualTo(TransactionStatus.REJECTED);
        assertThat(doc.getRejectionReason()).isEqualTo(RejectionReason.INSUFFICIENT_FUNDS);
    }

    @Test
    void duplicateEventsAreHarmless() {
        var doc = TransactionDocument.empty(txId).apply(created()).apply(created()).apply(completed()).apply(completed());

        assertThat(doc.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
        assertThat(doc.getParticipants()).hasSize(2);
    }

    private TransactionCreatedEvent created() {
        return new TransactionCreatedEvent(UUID.randomUUID(), txId, "A1", "B1", new BigDecimal("10.00"),
                CurrencyCode.AZN, alice, bob, "rent", T0);
    }

    private TransactionCompletedEvent completed() {
        return new TransactionCompletedEvent(UUID.randomUUID(), txId, "A1", "B1", new BigDecimal("10.00"),
                CurrencyCode.AZN, alice, bob, T1);
    }

    private TransactionRejectedEvent rejected() {
        return new TransactionRejectedEvent(UUID.randomUUID(), txId, "A1", "B1", new BigDecimal("10.00"),
                CurrencyCode.AZN, alice, bob, RejectionReason.INSUFFICIENT_FUNDS, "no money", T1);
    }
}
