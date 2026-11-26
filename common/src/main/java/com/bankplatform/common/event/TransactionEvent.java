package com.bankplatform.common.event;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Root of all transaction lifecycle events exchanged over Kafka.
 * <p>
 * The hierarchy is {@code sealed}, so consumers can use exhaustive pattern matching
 * ({@code switch (event) { case TransactionCreatedEvent e -> ... }}) and the compiler
 * reports any event type that is not handled.
 * <p>
 * Every event carries the full transfer data so each consumer can build its own view
 * without calling another service.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = TransactionCreatedEvent.class, name = "TransactionCreated"),
        @JsonSubTypes.Type(value = TransactionCompletedEvent.class, name = "TransactionCompleted"),
        @JsonSubTypes.Type(value = TransactionRejectedEvent.class, name = "TransactionRejected")
})
public sealed interface TransactionEvent
        permits TransactionCreatedEvent, TransactionCompletedEvent, TransactionRejectedEvent {

    /** Unique id of this event (used for de-duplication). */
    UUID eventId();

    UUID transactionId();

    String sourceAccount();

    String targetAccount();

    BigDecimal amount();

    CurrencyCode currency();

    /** Owner (user id) of the source account. */
    UUID sourceOwnerId();

    /** Owner (user id) of the target account. */
    UUID targetOwnerId();

    Instant occurredAt();

    /** Kafka topic this event type is published to. */
    String topic();
}
