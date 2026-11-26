package com.bankplatform.transaction.outbox;

import com.bankplatform.common.kafka.KafkaEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;

/**
 * Relays outbox rows to Kafka (at-least-once). A row is marked published only after the broker
 * acknowledged it. On failure the batch stops, so events of the same account keep their order;
 * the row is retried on the next tick. Consumers are idempotent, so a rare duplicate is harmless.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxPublisher {

    private final OutboxRepository outboxRepository;
    private final KafkaEventPublisher publisher;
    private final OutboxProperties properties;
    private final Clock clock;

    @Scheduled(fixedDelayString = "${app.outbox.poll-interval:500ms}")
    @Transactional
    public int publishPending() {
        List<OutboxEvent> batch = outboxRepository.lockUnpublished(properties.batchSize());
        int published = 0;
        for (OutboxEvent event : batch) {
            try {
                publisher.publish(event.getTopic(), event.getMessageKey(), event.getEventType(),
                        event.getPayload(), event.getCorrelationId());
                event.markPublished(clock.instant());
                published++;
            } catch (RuntimeException e) {
                event.markFailed(e.getMessage());
                log.warn("Outbox publish failed for event {} (attempt {}): {}",
                        event.getId(), event.getAttempts(), e.getMessage());
                break;
            }
        }
        if (published > 0) {
            log.debug("Published {} outbox event(s)", published);
        }
        return published;
    }

    @Scheduled(cron = "${app.outbox.cleanup-cron:0 0 3 * * *}")
    @Transactional
    public void purgePublished() {
        int deleted = outboxRepository.deletePublishedBefore(clock.instant().minus(properties.retention()));
        if (deleted > 0) {
            log.info("Purged {} published outbox event(s)", deleted);
        }
    }
}
