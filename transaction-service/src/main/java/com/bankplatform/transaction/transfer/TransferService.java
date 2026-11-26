package com.bankplatform.transaction.transfer;

import com.bankplatform.common.error.BusinessRuleException;
import com.bankplatform.common.error.ForbiddenException;
import com.bankplatform.common.error.NotFoundException;
import com.bankplatform.common.event.RejectionReason;
import com.bankplatform.common.event.TransactionCreatedEvent;
import com.bankplatform.common.security.AuthenticatedUser;
import com.bankplatform.transaction.account.Account;
import com.bankplatform.transaction.account.AccountRepository;
import com.bankplatform.transaction.outbox.OutboxService;
import com.bankplatform.transaction.transfer.dto.CreateTransferRequest;
import com.bankplatform.transaction.transfer.dto.TransactionResponse;
import com.bankplatform.common.web.PageResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

/**
 * Accepts transfers. A transfer is stored as {@code PENDING} together with a
 * {@link TransactionCreatedEvent} in the outbox - in <b>one</b> database transaction - so the event
 * can never be lost or published for a transfer that was rolled back.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TransferService {

    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final OutboxService outboxService;
    private final TransactionMapper transactionMapper;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public TransferResult createTransfer(AuthenticatedUser user, String idempotencyKey, CreateTransferRequest request) {
        String fingerprint = RequestFingerprint.of(request);

        Optional<Transaction> existing = transactionRepository.findByInitiatorIdAndIdempotencyKey(user.id(), idempotencyKey);
        if (existing.isPresent()) {
            return replay(existing.get(), fingerprint);
        }
        try {
            Transaction created = transactionTemplate.execute(status -> create(user, idempotencyKey, fingerprint, request));
            return new TransferResult(transactionMapper.toResponse(created), false);
        } catch (DataIntegrityViolationException e) {
            // A concurrent request with the same key committed first: answer with its result.
            return transactionRepository.findByInitiatorIdAndIdempotencyKey(user.id(), idempotencyKey)
                    .map(winner -> replay(winner, fingerprint))
                    .orElseThrow(() -> e);
        }
    }

    @Transactional(readOnly = true)
    public TransactionResponse get(AuthenticatedUser user, UUID id) {
        return transactionRepository.findById(id)
                .filter(t -> user.isAdmin() || t.getInitiatorId().equals(user.id()))
                .map(transactionMapper::toResponse)
                .orElseThrow(() -> new NotFoundException("Transaction", id));
    }

    @Transactional(readOnly = true)
    public PageResponse<TransactionResponse> mine(AuthenticatedUser user, Pageable pageable) {
        return PageResponse.of(transactionRepository.findByInitiatorId(user.id(), pageable), transactionMapper::toResponse);
    }

    @Transactional
    public void markCompleted(UUID transactionId) {
        transactionRepository.findById(transactionId).ifPresentOrElse(
                t -> {
                    if (t.complete(clock.instant())) {
                        log.info("Transaction {} completed", transactionId);
                    }
                },
                () -> log.warn("Completed event for unknown transaction {}", transactionId));
    }

    @Transactional
    public void markRejected(UUID transactionId, RejectionReason reason, String detail) {
        transactionRepository.findById(transactionId).ifPresentOrElse(
                t -> {
                    if (t.reject(reason, detail, clock.instant())) {
                        log.info("Transaction {} rejected: {}", transactionId, reason);
                    }
                },
                () -> log.warn("Rejected event for unknown transaction {}", transactionId));
    }

    private Transaction create(AuthenticatedUser user, String idempotencyKey, String fingerprint,
                               CreateTransferRequest request) {
        if (request.sourceAccount().equals(request.targetAccount())) {
            throw new BusinessRuleException("same-account", "Invalid transfer",
                    "Source and target account must be different");
        }
        Account source = accountRepository.findByNumber(request.sourceAccount())
                .orElseThrow(() -> new NotFoundException("Account", request.sourceAccount()));
        if (!source.isOwnedBy(user.id())) {
            throw new ForbiddenException("You can only transfer from your own accounts");
        }
        Account target = accountRepository.findByNumber(request.targetAccount())
                .orElseThrow(() -> new NotFoundException("Account", request.targetAccount()));
        if (source.getCurrency() != request.currency() || target.getCurrency() != request.currency()) {
            throw new BusinessRuleException("currency-mismatch", "Currency mismatch",
                    "Both accounts must be in " + request.currency()
                            + " (source: " + source.getCurrency() + ", target: " + target.getCurrency() + ")");
        }

        Transaction transaction = transactionRepository.saveAndFlush(new Transaction(user.id(), idempotencyKey,
                fingerprint, source.getNumber(), target.getNumber(), request.amount(), request.currency(),
                request.description(), clock.instant()));

        outboxService.enqueue(new TransactionCreatedEvent(UUID.randomUUID(), transaction.getId(),
                transaction.getSourceAccount(), transaction.getTargetAccount(), transaction.getAmount(),
                transaction.getCurrency(), source.getOwnerId(), target.getOwnerId(), transaction.getDescription(),
                transaction.getCreatedAt()));

        log.info("Accepted transfer {} {} {} -> {}", transaction.getId(), transaction.getAmount(),
                transaction.getSourceAccount(), transaction.getTargetAccount());
        return transaction;
    }

    private TransferResult replay(Transaction existing, String fingerprint) {
        if (!existing.getRequestHash().equals(fingerprint)) {
            throw new BusinessRuleException("idempotency-key-reused", "Idempotency key reused",
                    "This Idempotency-Key was already used for a different request");
        }
        log.info("Idempotent replay of transaction {}", existing.getId());
        return new TransferResult(transactionMapper.toResponse(existing), true);
    }
}
