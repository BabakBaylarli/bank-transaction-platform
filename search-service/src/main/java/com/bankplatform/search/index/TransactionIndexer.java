package com.bankplatform.search.index;

import com.bankplatform.common.event.TransactionEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Upserts the read model. A concurrent write is detected by Elasticsearch's sequence numbers
 * (OptimisticLockingFailureException) and the Kafka error handler simply retries the record.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionIndexer {

    private final TransactionDocumentRepository repository;

    public TransactionDocument index(TransactionEvent event) {
        TransactionDocument document = repository.findById(event.transactionId().toString())
                .orElseGet(() -> TransactionDocument.empty(event.transactionId()))
                .apply(event);
        TransactionDocument saved = repository.save(document);
        log.debug("Indexed {} -> {}", event.transactionId(), saved.getStatus());
        return saved;
    }
}
