package com.bankplatform.processing.processor;

import com.bankplatform.common.event.CurrencyCode;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionProcessorTest {

    private static final Instant NOW = Instant.parse("2026-05-10T15:30:00Z");
    private static final String SOURCE = "AZ00BTPX00000000000000000001";
    private static final String TARGET = "AZ00BTPX00000000000000000002";

    @Mock
    private FraudEngine fraudEngine;
    @Mock
    private LedgerAccountRepository accounts;
    @Mock
    private ProcessedTransactionRepository processed;

    private TransactionProcessor processor;
    private LedgerAccount source;
    private LedgerAccount target;

    @BeforeEach
    void setUp() {
        processor = new TransactionProcessor(fraudEngine, accounts, processed, Clock.fixed(NOW, ZoneOffset.UTC));
        source = new LedgerAccount(UUID.randomUUID(), SOURCE, UUID.randomUUID(), CurrencyCode.AZN, new BigDecimal("100.00"));
        target = new LedgerAccount(UUID.randomUUID(), TARGET, UUID.randomUUID(), CurrencyCode.AZN, new BigDecimal("5.00"));
    }

    @Test
    void movesMoneyAndRecordsCompletedDecision() {
        when(fraudEngine.check(any())).thenReturn(FraudCheckResult.PASSED);
        givenAccounts();

        TransactionEvent result = processor.process(transfer("40.00"));

        assertThat(result).isInstanceOf(TransactionCompletedEvent.class);
        assertThat(source.getBalance()).isEqualByComparingTo("60.00");
        assertThat(target.getBalance()).isEqualByComparingTo("45.00");
        assertThat(savedDecision().getStatus()).isEqualTo(TransactionStatus.COMPLETED);
    }

    @Test
    void rejectsInsufficientFundsWithoutTouchingBalances() {
        when(fraudEngine.check(any())).thenReturn(FraudCheckResult.PASSED);
        givenAccounts();

        TransactionEvent result = processor.process(transfer("100.01"));

        assertThat(result).isInstanceOfSatisfying(TransactionRejectedEvent.class,
                r -> assertThat(r.reason()).isEqualTo(RejectionReason.INSUFFICIENT_FUNDS));
        assertThat(source.getBalance()).isEqualByComparingTo("100.00");
        assertThat(target.getBalance()).isEqualByComparingTo("5.00");
    }

    @Test
    void fraudViolationRejectsBeforeLoadingAccounts() {
        when(fraudEngine.check(any())).thenReturn(
                new FraudCheckResult.Violation(RejectionReason.VELOCITY_LIMIT_EXCEEDED, "too fast"));

        TransactionEvent result = processor.process(transfer("1.00"));

        assertThat(result).isInstanceOfSatisfying(TransactionRejectedEvent.class,
                r -> assertThat(r.reason()).isEqualTo(RejectionReason.VELOCITY_LIMIT_EXCEEDED));
        verify(accounts, never()).findByNumber(any());
        assertThat(savedDecision().getRejectionReason()).isEqualTo(RejectionReason.VELOCITY_LIMIT_EXCEEDED);
    }

    @Test
    void unknownAccountIsRejected() {
        when(fraudEngine.check(any())).thenReturn(FraudCheckResult.PASSED);
        when(accounts.findByNumber(SOURCE)).thenReturn(Optional.of(source));
        when(accounts.findByNumber(TARGET)).thenReturn(Optional.empty());

        assertThat(processor.process(transfer("1.00"))).isInstanceOfSatisfying(TransactionRejectedEvent.class,
                r -> assertThat(r.reason()).isEqualTo(RejectionReason.ACCOUNT_NOT_FOUND));
    }

    @Test
    void redeliveredEventReturnsStoredDecisionAndDoesNotMoveMoneyTwice() {
        TransactionCreatedEvent event = transfer("40.00");
        when(processed.findById(event.transactionId())).thenReturn(Optional.of(new ProcessedTransaction(
                event.transactionId(), SOURCE, TARGET, event.amount(), CurrencyCode.AZN, TransactionStatus.COMPLETED,
                null, null, NOW)));

        TransactionEvent first = processor.process(event);
        TransactionEvent second = processor.process(event);

        assertThat(first).isInstanceOf(TransactionCompletedEvent.class).isEqualTo(second);
        verify(fraudEngine, never()).check(any());
        verify(accounts, never()).findByNumber(any());
        verify(processed, never()).save(any());
    }

    private void givenAccounts() {
        when(accounts.findByNumber(SOURCE)).thenReturn(Optional.of(source));
        when(accounts.findByNumber(TARGET)).thenReturn(Optional.of(target));
    }

    private ProcessedTransaction savedDecision() {
        ArgumentCaptor<ProcessedTransaction> captor = ArgumentCaptor.forClass(ProcessedTransaction.class);
        verify(processed).save(captor.capture());
        return captor.getValue();
    }

    private static TransactionCreatedEvent transfer(String amount) {
        return new TransactionCreatedEvent(UUID.randomUUID(), UUID.randomUUID(), SOURCE, TARGET,
                new BigDecimal(amount), CurrencyCode.AZN, UUID.randomUUID(), UUID.randomUUID(), "test", NOW);
    }
}
