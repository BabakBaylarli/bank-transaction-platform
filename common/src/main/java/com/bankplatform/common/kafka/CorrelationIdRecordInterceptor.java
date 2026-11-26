package com.bankplatform.common.kafka;

import com.bankplatform.common.logging.CorrelationId;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.kafka.listener.RecordInterceptor;

import java.nio.charset.StandardCharsets;

/** Restores the correlation id from the Kafka record header into the MDC of the listener thread. */
public class CorrelationIdRecordInterceptor implements RecordInterceptor<Object, Object> {

    @Override
    public ConsumerRecord<Object, Object> intercept(ConsumerRecord<Object, Object> record,
                                                    Consumer<Object, Object> consumer) {
        Header header = record.headers().lastHeader(CorrelationId.HEADER);
        String id = header != null
                ? new String(header.value(), StandardCharsets.UTF_8)
                : CorrelationId.newId();
        CorrelationId.set(id);
        return record;
    }

    @Override
    public void afterRecord(ConsumerRecord<Object, Object> record, Consumer<Object, Object> consumer) {
        CorrelationId.clear();
    }
}
