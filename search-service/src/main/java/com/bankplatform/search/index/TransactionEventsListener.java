package com.bankplatform.search.index;

import com.bankplatform.common.event.EventJson;
import com.bankplatform.common.kafka.KafkaTopics;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TransactionEventsListener {

    private final TransactionIndexer indexer;

    @KafkaListener(groupId = "search-service", concurrency = "${app.kafka.listener-concurrency:3}", topics = {
            KafkaTopics.TRANSACTIONS_CREATED, KafkaTopics.TRANSACTIONS_COMPLETED, KafkaTopics.TRANSACTIONS_REJECTED})
    public void onEvent(String payload) {
        indexer.index(EventJson.read(payload));
    }
}
