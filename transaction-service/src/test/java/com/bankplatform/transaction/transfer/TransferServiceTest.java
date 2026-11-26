package com.bankplatform.transaction.transfer;

import com.bankplatform.common.error.BusinessRuleException;
import com.bankplatform.common.error.ForbiddenException;
import com.bankplatform.common.error.NotFoundException;
import com.bankplatform.common.event.CurrencyCode;
import com.bankplatform.common.event.RejectionReason;
import com.bankplatform.common.event.TransactionCreatedEvent;
import com.bankplatform.common.event.TransactionStatus;
import com.bankplatform.common.security.AuthenticatedUser;
import com.bankplatform.common.security.Role;
import com.bankplatform.transaction.account.Account;
import com.bankplatform.transaction.account.AccountRepository;
import com.bankplatform.transaction.outbox.OutboxService;
import com.bankplatform.transaction.transfer.dto.CreateTransferRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransferServiceTest {

    private static final Instant NOW = Instant.parse("2026-03-01T12:00:00Z");
    private static final String KEY = "key-0000-0001";
    private static final String SOURCE = "AZ00BTPX00000000000000000001";
    private static final String TARGET = "AZ00BTPX00000000000000000002";

    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private AccountRepository accountRepository;
    @Mock
    private OutboxService outboxService;
    @Mock
    private TransactionTemplate transactionTemplate;

    private final AuthenticatedUser alice = new AuthenticatedUser(UUID.randomUUID(), "alice", Role.CUSTOMER);
    private final UUID bobId = UUID.randomUUID();

    private TransferService service;

    @BeforeEach
    void setUp() {
        service = new TransferService(transactionRepository, accountRepository, outboxService,
                Mappers.getMapper(TransactionMapper.class), transactionTemplate, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void storesPendingTransactionAndEnqueuesEventInSameTransaction() {
        runTemplateInline();
        givenAccounts(CurrencyCode.AZN, CurrencyCode.AZN);
        when(transactionRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        TransferResult result = service.createTransfer(alice, KEY, request("100.50", CurrencyCode.AZN));

        assertThat(result.replayed()).isFalse();
        assertThat(result.transaction().status()).isEqualTo(TransactionStatus.PENDING);
        assertThat(result.transaction().amount()).isEqualByComparingTo("100.50");

        ArgumentCaptor<TransactionCreatedEvent> event = ArgumentCaptor.forClass(TransactionCreatedEvent.class);
        verify(outboxService).enqueue(event.capture());
        assertThat(event.getValue().transactionId()).isEqualTo(result.transaction().id());
        assertThat(event.getValue().sourceOwnerId()).isEqualTo(alice.id());
        assertThat(event.getValue().targetOwnerId()).isEqualTo(bobId);
    }

    @Test
    void sameKeyAndSamePayloadReplaysOriginalResult() {
        CreateTransferRequest request = request("10.00", CurrencyCode.AZN);
        Transaction original = new Transaction(alice.id(), KEY, RequestFingerprint.of(request), SOURCE, TARGET,
                new BigDecimal("10.00"), CurrencyCode.AZN, null, NOW);
        when(transactionRepository.findByInitiatorIdAndIdempotencyKey(alice.id(), KEY)).thenReturn(Optional.of(original));

        // "10" and "10.00" are the same amount -> same fingerprint
        TransferResult result = service.createTransfer(alice, KEY, request("10", CurrencyCode.AZN));

        assertThat(result.replayed()).isTrue();
        assertThat(result.transaction().id()).isEqualTo(original.getId());
        verify(transactionTemplate, never()).execute(any());
    }

    @Test
    void sameKeyWithDifferentPayloadIsRejected() {
        Transaction original = new Transaction(alice.id(), KEY, RequestFingerprint.of(request("10.00", CurrencyCode.AZN)),
                SOURCE, TARGET, new BigDecimal("10.00"), CurrencyCode.AZN, null, NOW);
        when(transactionRepository.findByInitiatorIdAndIdempotencyKey(alice.id(), KEY)).thenReturn(Optional.of(original));

        assertThatThrownBy(() -> service.createTransfer(alice, KEY, request("99.00", CurrencyCode.AZN)))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("errorCode").isEqualTo("idempotency-key-reused");
    }

    @Test
    void concurrentDuplicateResolvesToTheWinningRequest() {
        CreateTransferRequest request = request("10.00", CurrencyCode.AZN);
        Transaction winner = new Transaction(alice.id(), KEY, RequestFingerprint.of(request), SOURCE, TARGET,
                new BigDecimal("10.00"), CurrencyCode.AZN, null, NOW);
        when(transactionRepository.findByInitiatorIdAndIdempotencyKey(alice.id(), KEY))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(winner));
        when(transactionTemplate.execute(any())).thenThrow(new DataIntegrityViolationException("duplicate key"));

        TransferResult result = service.createTransfer(alice, KEY, request);

        assertThat(result.replayed()).isTrue();
        assertThat(result.transaction().id()).isEqualTo(winner.getId());
    }

    @Test
    void cannotTransferFromSomeoneElsesAccount() {
        runTemplateInline();
        when(accountRepository.findByNumber(SOURCE))
                .thenReturn(Optional.of(new Account(SOURCE, bobId, CurrencyCode.AZN, BigDecimal.TEN, NOW)));

        assertThatThrownBy(() -> service.createTransfer(alice, KEY, request("1.00", CurrencyCode.AZN)))
                .isInstanceOf(ForbiddenException.class);
        verify(outboxService, never()).enqueue(any());
    }

    @Test
    void rejectsCurrencyMismatch() {
        runTemplateInline();
        givenAccounts(CurrencyCode.AZN, CurrencyCode.USD);

        assertThatThrownBy(() -> service.createTransfer(alice, KEY, request("1.00", CurrencyCode.AZN)))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("errorCode").isEqualTo("currency-mismatch");
    }

    @Test
    void rejectsTransferToSameAccount() {
        runTemplateInline();
        var request = new CreateTransferRequest(SOURCE, SOURCE, BigDecimal.ONE, CurrencyCode.AZN, null);

        assertThatThrownBy(() -> service.createTransfer(alice, KEY, request))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("errorCode").isEqualTo("same-account");
    }

    @Test
    void unknownTargetAccountIsNotFound() {
        runTemplateInline();
        when(accountRepository.findByNumber(SOURCE))
                .thenReturn(Optional.of(new Account(SOURCE, alice.id(), CurrencyCode.AZN, BigDecimal.TEN, NOW)));
        when(accountRepository.findByNumber(TARGET)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createTransfer(alice, KEY, request("1.00", CurrencyCode.AZN)))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void finalStatusIsNeverOverwrittenByDuplicateEvents() {
        Transaction tx = new Transaction(alice.id(), KEY, "hash", SOURCE, TARGET, BigDecimal.ONE,
                CurrencyCode.AZN, null, NOW);
        when(transactionRepository.findById(tx.getId())).thenReturn(Optional.of(tx));

        service.markCompleted(tx.getId());
        service.markRejected(tx.getId(), RejectionReason.INSUFFICIENT_FUNDS, "late duplicate");

        assertThat(tx.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
        assertThat(tx.getRejectionReason()).isNull();
    }

    private void givenAccounts(CurrencyCode sourceCurrency, CurrencyCode targetCurrency) {
        when(accountRepository.findByNumber(SOURCE))
                .thenReturn(Optional.of(new Account(SOURCE, alice.id(), sourceCurrency, new BigDecimal("500.00"), NOW)));
        when(accountRepository.findByNumber(TARGET))
                .thenReturn(Optional.of(new Account(TARGET, bobId, targetCurrency, BigDecimal.ZERO, NOW)));
    }

    @SuppressWarnings("unchecked")
    private void runTemplateInline() {
        when(transactionTemplate.execute(any()))
                .thenAnswer(inv -> ((TransactionCallback<Object>) inv.getArgument(0)).doInTransaction(null));
    }

    private static CreateTransferRequest request(String amount, CurrencyCode currency) {
        return new CreateTransferRequest(SOURCE, TARGET, new BigDecimal(amount), currency, null);
    }
}
