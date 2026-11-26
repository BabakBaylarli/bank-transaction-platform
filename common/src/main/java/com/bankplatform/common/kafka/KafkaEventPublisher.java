package com.bankplatform.common.kafka;

import com.bankplatform.common.event.EventJson;
import com.bankplatform.common.event.TransactionEvent;
import com.bankplatform.common.logging.CorrelationId;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Publishes events synchronously (waits for the broker ack) and always attaches the correlation id
 * and the event type as record headers.
 * <p>
 * Records are keyed by the <b>source account</b>, so all events of one account land on the same
 * partition and are processed in order.
 */
public class KafkaEventPublisher {

    public static final String EVENT_TYPE_HEADER = "eventType";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final Duration sendTimeout;

    public KafkaEventPublisher(KafkaTemplate<String, String> kafkaTemplate, Duration sendTimeout) {
        this.kafkaTemplate = kafkaTemplate;
        this.sendTimeout = sendTimeout;
    }

    public SendResult<String, String> publish(TransactionEvent event) {
        return publish(event.topic(), event.sourceAccount(), event.getClass().getSimpleName(),
                EventJson.write(event), CorrelationId.currentOrNew());
    }

    public SendResult<String, String> publish(String topic, String key, String eventType,
                                              String payload, String correlationId) {
        ProducerRecord<String, String> record = new ProducerRecord<>(topic, key, payload);
        record.headers().add(EVENT_TYPE_HEADER, eventType.getBytes(StandardCharsets.UTF_8));
        if (correlationId != null) {
            record.headers().add(CorrelationId.HEADER, correlationId.getBytes(StandardCharsets.UTF_8));
        }
        try {
            return kafkaTemplate.send(record).get(sendTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new EventPublishingException("Interrupted while publishing to " + topic, e);
        } catch (ExecutionException | TimeoutException e) {
            throw new EventPublishingException("Failed to publish to " + topic, e);
        }
    }

    public static class EventPublishingException extends RuntimeException {
        public EventPublishingException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
