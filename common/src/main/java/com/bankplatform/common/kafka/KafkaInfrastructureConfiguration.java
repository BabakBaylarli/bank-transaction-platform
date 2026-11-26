package com.bankplatform.common.kafka;

import com.bankplatform.common.event.InvalidEventException;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

/**
 * Declares all platform topics (idempotent - whichever service starts first creates them with the
 * right partition count) and a sensible default error handler for listeners.
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
public class KafkaInfrastructureConfiguration {

    @Bean
    @ConditionalOnProperty(name = "app.kafka.create-topics", havingValue = "true", matchIfMissing = true)
    public KafkaAdmin.NewTopics platformTopics(@Value("${app.kafka.partitions:3}") int partitions,
                                               @Value("${app.kafka.replicas:1}") int replicas) {
        return new KafkaAdmin.NewTopics(
                topic(KafkaTopics.TRANSACTIONS_CREATED, partitions, replicas),
                topic(KafkaTopics.TRANSACTIONS_COMPLETED, partitions, replicas),
                topic(KafkaTopics.TRANSACTIONS_REJECTED, partitions, replicas),
                topic(KafkaTopics.TRANSACTIONS_CREATED_DLT, partitions, replicas));
    }

    /**
     * Default: retry transient failures with exponential backoff, never retry poison messages,
     * then log and skip. Services that need a DLT (processing-service) define their own handler.
     */
    @Bean
    @ConditionalOnMissingBean(CommonErrorHandler.class)
    public CommonErrorHandler defaultKafkaErrorHandler() {
        ExponentialBackOff backOff = new ExponentialBackOff(500, 2.0);
        backOff.setMaxElapsedTime(15_000);
        DefaultErrorHandler handler = new DefaultErrorHandler(
                (record, ex) -> log.error("Giving up on record {}-{}@{}: {}",
                        record.topic(), record.partition(), record.offset(), ex.getMessage()),
                backOff);
        handler.addNotRetryableExceptions(InvalidEventException.class);
        return handler;
    }

    private static NewTopic topic(String name, int partitions, int replicas) {
        return TopicBuilder.name(name).partitions(partitions).replicas(replicas).build();
    }
}
