package com.bankplatform.common.event;

import com.bankplatform.common.kafka.KafkaTopics;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** A transfer was accepted by the API and is waiting to be processed. */
public record TransactionCreatedEvent(
        UUID eventId,
        UUID transactionId,
        String sourceAccount,
        String targetAccount,
        BigDecimal amount,
        CurrencyCode currency,
        UUID sourceOwnerId,
        UUID targetOwnerId,
        String description,
        Instant occurredAt
) implements TransactionEvent {

    @Override
    public String topic() {
        return KafkaTopics.TRANSACTIONS_CREATED;
    }
}
