package com.bankplatform.processing.config;

import com.bankplatform.common.event.InvalidEventException;
import com.bankplatform.common.kafka.KafkaTopics;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;

import java.time.Duration;

/**
 * Blocking retries with exponential backoff (keeps per-account ordering), then the record is
 * parked on {@value KafkaTopics#TRANSACTIONS_CREATED_DLT} with the exception in its headers.
 * Poison messages (unparseable payloads) skip the retries and go to the DLT immediately.
 */
@Slf4j
@Configuration
public class KafkaErrorHandlingConfig {

    @Bean
    CommonErrorHandler deadLetterErrorHandler(KafkaTemplate<String, String> template,
                                              @Value("${app.kafka.retry.max-attempts:4}") int maxRetries,
                                              @Value("${app.kafka.retry.initial-interval:1s}") Duration initial,
                                              @Value("${app.kafka.retry.max-interval:10s}") Duration max) {
        var recoverer = new DeadLetterPublishingRecoverer(template, (record, ex) -> {
            log.error("Sending record {}-{}@{} to DLT: {}", record.topic(), record.partition(), record.offset(),
                    ex.getMessage());
            return new TopicPartition(KafkaTopics.TRANSACTIONS_CREATED_DLT, record.partition());
        });
        var backOff = new ExponentialBackOffWithMaxRetries(maxRetries);
        backOff.setInitialInterval(initial.toMillis());
        backOff.setMultiplier(2.0);
        backOff.setMaxInterval(max.toMillis());

        var handler = new DefaultErrorHandler(recoverer, backOff);
        handler.addNotRetryableExceptions(InvalidEventException.class);
        handler.setRetryListeners((record, ex, attempt) ->
                log.warn("Retry {} for record {}-{}@{}: {}", attempt, record.topic(), record.partition(),
                        record.offset(), ex.getMessage()));
        return handler;
    }
}
