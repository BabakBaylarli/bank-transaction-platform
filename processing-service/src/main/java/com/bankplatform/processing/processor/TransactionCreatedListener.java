package com.bankplatform.processing.processor;

import com.bankplatform.common.event.EventJson;
import com.bankplatform.common.event.TransactionCreatedEvent;
import com.bankplatform.common.event.TransactionEvent;
import com.bankplatform.common.kafka.KafkaEventPublisher;
import com.bankplatform.common.kafka.KafkaTopics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes new transfers. The result event is published <i>after</i> the DB commit; if publishing
 * fails, the listener throws, the record is redelivered, and {@link TransactionProcessor} returns the
 * already-committed decision - so the result is eventually published exactly once per decision.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TransactionCreatedListener {

    private final TransactionProcessor processor;
    private final KafkaEventPublisher publisher;

    @KafkaListener(topics = KafkaTopics.TRANSACTIONS_CREATED, groupId = "processing-service",
            concurrency = "${app.kafka.listener-concurrency:3}")
    public void onCreated(String payload) {
        TransactionEvent event = EventJson.read(payload);
        if (!(event instanceof TransactionCreatedEvent created)) {
            log.warn("Unexpected event type {} on {}", event.getClass().getSimpleName(), KafkaTopics.TRANSACTIONS_CREATED);
            return;
        }
        publisher.publish(processor.process(created));
    }
}
