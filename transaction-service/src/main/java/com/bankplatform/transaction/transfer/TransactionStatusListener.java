package com.bankplatform.transaction.transfer;

import com.bankplatform.common.event.EventJson;
import com.bankplatform.common.event.TransactionCompletedEvent;
import com.bankplatform.common.event.TransactionCreatedEvent;
import com.bankplatform.common.event.TransactionRejectedEvent;
import com.bankplatform.common.kafka.KafkaTopics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** Moves transfers from PENDING to their final status once processing-service has decided. */
@Slf4j
@Component
@RequiredArgsConstructor
public class TransactionStatusListener {

    private final TransferService transferService;

    @KafkaListener(topics = {KafkaTopics.TRANSACTIONS_COMPLETED, KafkaTopics.TRANSACTIONS_REJECTED},
            groupId = "transaction-service")
    public void onResult(String payload) {
        switch (EventJson.read(payload)) {
            case TransactionCompletedEvent e -> transferService.markCompleted(e.transactionId());
            case TransactionRejectedEvent e -> transferService.markRejected(e.transactionId(), e.reason(), e.detail());
            case TransactionCreatedEvent e -> log.debug("Ignoring created event {}", e.transactionId());
        }
    }
}
