package com.bankplatform.transaction.outbox;

import com.bankplatform.common.kafka.KafkaEventPublisher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxPublisherTest {

    private static final Instant NOW = Instant.parse("2026-03-01T12:00:00Z");

    @Mock
    private OutboxRepository repository;
    @Mock
    private KafkaEventPublisher kafka;

    private OutboxPublisher publisher() {
        return new OutboxPublisher(repository, kafka, new OutboxProperties(10, Duration.ofDays(7)),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void marksEventsPublishedAfterBrokerAck() {
        OutboxEvent first = event("k1");
        OutboxEvent second = event("k2");
        when(repository.lockUnpublished(10)).thenReturn(List.of(first, second));

        assertThat(publisher().publishPending()).isEqualTo(2);

        assertThat(first.getPublishedAt()).isEqualTo(NOW);
        assertThat(second.getPublishedAt()).isEqualTo(NOW);
    }

    @Test
    void stopsBatchOnFailureToPreserveOrdering() {
        OutboxEvent failing = event("k1");
        OutboxEvent next = event("k2");
        when(repository.lockUnpublished(10)).thenReturn(List.of(failing, next));
        doThrow(new KafkaEventPublisher.EventPublishingException("broker down", null))
                .when(kafka).publish(any(), eq("k1"), any(), any(), any());

        assertThat(publisher().publishPending()).isZero();

        assertThat(failing.getPublishedAt()).isNull();
        assertThat(failing.getAttempts()).isEqualTo(1);
        assertThat(failing.getLastError()).isEqualTo("broker down");
        verify(kafka, never()).publish(any(), eq("k2"), any(), any(), any());
    }

    private static OutboxEvent event(String key) {
        return new OutboxEvent(UUID.randomUUID(), UUID.randomUUID(), "TransactionCreatedEvent",
                "transactions.created", key, "{}", "corr-1", NOW);
    }
}
