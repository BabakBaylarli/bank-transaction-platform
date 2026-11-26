package com.bankplatform.common.event;

import com.bankplatform.common.kafka.KafkaTopics;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** The transfer was refused (fraud rule, insufficient funds, ...). No money was moved. */
public record TransactionRejectedEvent(
        UUID eventId,
        UUID transactionId,
        String sourceAccount,
        String targetAccount,
        BigDecimal amount,
        CurrencyCode currency,
        UUID sourceOwnerId,
        UUID targetOwnerId,
        RejectionReason reason,
        String detail,
        Instant occurredAt
) implements TransactionEvent {

    @Override
    public String topic() {
        return KafkaTopics.TRANSACTIONS_REJECTED;
    }
}
