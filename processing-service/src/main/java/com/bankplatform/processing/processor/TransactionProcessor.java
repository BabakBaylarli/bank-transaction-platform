package com.bankplatform.processing.processor;

import com.bankplatform.common.event.RejectionReason;
import com.bankplatform.common.event.TransactionCompletedEvent;
import com.bankplatform.common.event.TransactionCreatedEvent;
import com.bankplatform.common.event.TransactionEvent;
import com.bankplatform.common.event.TransactionRejectedEvent;
import com.bankplatform.common.event.TransactionStatus;
import com.bankplatform.processing.fraud.FraudCheckResult;
import com.bankplatform.processing.fraud.FraudEngine;
import com.bankplatform.processing.ledger.LedgerAccount;
import com.bankplatform.processing.ledger.LedgerAccountRepository;
import com.bankplatform.processing.ledger.ProcessedTransaction;
import com.bankplatform.processing.ledger.ProcessedTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Decides and applies a transfer. Fraud checks, the debit, the credit and the
 * {@link ProcessedTransaction} record are committed atomically in a single DB transaction.
 * <p>
 * Idempotent: if the event was already processed (redelivery after a crash or a failed publish),
 * the stored decision is returned again and no money is moved twice.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionProcessor {

    private final FraudEngine fraudEngine;
    private final LedgerAccountRepository accountRepository;
    private final ProcessedTransactionRepository processedRepository;
    private final Clock clock;

    @Transactional
    public TransactionEvent process(TransactionCreatedEvent transfer) {
        Optional<ProcessedTransaction> previous = processedRepository.findById(transfer.transactionId());
        if (previous.isPresent()) {
            log.info("Transaction {} already processed ({}), re-emitting result",
                    transfer.transactionId(), previous.get().getStatus());
            return toResultEvent(transfer, previous.get());
        }

        Instant now = clock.instant();
        ProcessedTransaction outcome = switch (fraudEngine.check(transfer)) {
            case FraudCheckResult.Violation v -> reject(transfer, v.reason(), v.detail(), now);
            case FraudCheckResult.Passed p -> transfer(transfer, now);
        };
        processedRepository.save(outcome);
        log.info("Transaction {} -> {}{}", transfer.transactionId(), outcome.getStatus(),
                outcome.getRejectionReason() != null ? " (" + outcome.getRejectionReason() + ")" : "");
        return toResultEvent(transfer, outcome);
    }

    private ProcessedTransaction transfer(TransactionCreatedEvent transfer, Instant now) {
        Optional<LedgerAccount> source = accountRepository.findByNumber(transfer.sourceAccount());
        Optional<LedgerAccount> target = accountRepository.findByNumber(transfer.targetAccount());
        if (source.isEmpty() || target.isEmpty()) {
            return reject(transfer, RejectionReason.ACCOUNT_NOT_FOUND, "Source or target account does not exist", now);
        }
        if (source.get().getCurrency() != transfer.currency() || target.get().getCurrency() != transfer.currency()) {
            return reject(transfer, RejectionReason.CURRENCY_MISMATCH, "Account currency differs from transfer currency", now);
        }
        if (!source.get().canDebit(transfer.amount())) {
            return reject(transfer, RejectionReason.INSUFFICIENT_FUNDS, "Insufficient funds", now);
        }
        source.get().debit(transfer.amount(), now);
        target.get().credit(transfer.amount(), now);
        return new ProcessedTransaction(transfer.transactionId(), transfer.sourceAccount(), transfer.targetAccount(),
                transfer.amount(), transfer.currency(), TransactionStatus.COMPLETED, null, null, now);
    }

    private static ProcessedTransaction reject(TransactionCreatedEvent transfer, RejectionReason reason,
                                               String detail, Instant now) {
        return new ProcessedTransaction(transfer.transactionId(), transfer.sourceAccount(), transfer.targetAccount(),
                transfer.amount(), transfer.currency(), TransactionStatus.REJECTED, reason, detail, now);
    }

    /** Deterministic event id: re-emitting the same decision produces an identical event. */
    private static TransactionEvent toResultEvent(TransactionCreatedEvent t, ProcessedTransaction p) {
        UUID eventId = UUID.nameUUIDFromBytes((t.transactionId() + ":" + p.getStatus()).getBytes(StandardCharsets.UTF_8));
        return p.getStatus() == TransactionStatus.COMPLETED
                ? new TransactionCompletedEvent(eventId, t.transactionId(), t.sourceAccount(), t.targetAccount(),
                t.amount(), t.currency(), t.sourceOwnerId(), t.targetOwnerId(), p.getProcessedAt())
                : new TransactionRejectedEvent(eventId, t.transactionId(), t.sourceAccount(), t.targetAccount(),
                t.amount(), t.currency(), t.sourceOwnerId(), t.targetOwnerId(), p.getRejectionReason(),
                p.getRejectionDetail(), p.getProcessedAt());
    }
}
