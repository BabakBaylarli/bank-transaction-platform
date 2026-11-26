package com.bankplatform.common.event;

import com.bankplatform.common.kafka.KafkaTopics;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Funds were moved from the source to the target account. */
public record TransactionCompletedEvent(
        UUID eventId,
        UUID transactionId,
        String sourceAccount,
        String targetAccount,
        BigDecimal amount,
        CurrencyCode currency,
        UUID sourceOwnerId,
        UUID targetOwnerId,
        Instant occurredAt
) implements TransactionEvent {

    @Override
    public String topic() {
        return KafkaTopics.TRANSACTIONS_COMPLETED;
    }
}
