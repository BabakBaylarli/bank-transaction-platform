package com.bankplatform.processing.fraud;

import com.bankplatform.common.event.CurrencyCode;
import com.bankplatform.common.event.RejectionReason;
import com.bankplatform.common.event.TransactionCreatedEvent;
import com.bankplatform.processing.ledger.ProcessedTransactionRepository;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FraudRulesTest {

    private static final Instant NOW = Instant.parse("2026-05-10T15:30:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final String ACCOUNT = "AZ00BTPX00000000000000000001";
    private static final FraudProperties LIMITS =
            new FraudProperties(new BigDecimal("10000.00"), new BigDecimal("20000.00"), 5);

    @Mock
    private ProcessedTransactionRepository repository;

    static TransactionCreatedEvent transfer(String amount) {
        return new TransactionCreatedEvent(UUID.randomUUID(), UUID.randomUUID(), ACCOUNT, "AZ00BTPX00000000000000000002",
                new BigDecimal(amount), CurrencyCode.AZN, UUID.randomUUID(), UUID.randomUUID(), null, NOW);
    }

    @Nested
    class AmountLimit {

        private final AmountLimitRule rule = new AmountLimitRule(LIMITS);

        @Test
        void amountEqualToLimitPasses() {
            assertThat(rule.evaluate(transfer("10000.00"))).isEqualTo(FraudCheckResult.PASSED);
        }

        @Test
        void amountAboveLimitIsRejected() {
            assertThat(rule.evaluate(transfer("10000.01")))
                    .isInstanceOfSatisfying(FraudCheckResult.Violation.class,
                            v -> assertThat(v.reason()).isEqualTo(RejectionReason.AMOUNT_LIMIT_EXCEEDED));
        }
    }

    @Nested
    class DailyLimit {

        @Test
        void sumsOnlyTodaysCompletedOutgoingTransfers() {
            Instant startOfDay = Instant.parse("2026-05-10T00:00:00Z");
            when(repository.sumCompletedOutgoingSince(ACCOUNT, startOfDay)).thenReturn(new BigDecimal("15000.00"));
            var rule = new DailyLimitRule(repository, LIMITS, CLOCK);

            assertThat(rule.evaluate(transfer("5000.00"))).isEqualTo(FraudCheckResult.PASSED);
            assertThat(rule.evaluate(transfer("5000.01")))
                    .isInstanceOfSatisfying(FraudCheckResult.Violation.class,
                            v -> assertThat(v.reason()).isEqualTo(RejectionReason.DAILY_LIMIT_EXCEEDED));
        }
    }

    @Nested
    class Velocity {

        @Test
        void rejectsWhenMaxTransfersPerMinuteReached() {
            when(repository.countBySourceAccountAndProcessedAtAfter(eq(ACCOUNT), eq(NOW.minusSeconds(60))))
                    .thenReturn(4L, 5L);
            var rule = new VelocityRule(repository, LIMITS, CLOCK);

            assertThat(rule.evaluate(transfer("1.00"))).isEqualTo(FraudCheckResult.PASSED);
            assertThat(rule.evaluate(transfer("1.00")))
                    .isInstanceOfSatisfying(FraudCheckResult.Violation.class,
                            v -> assertThat(v.reason()).isEqualTo(RejectionReason.VELOCITY_LIMIT_EXCEEDED));
        }
    }

    @Nested
    class Engine {

        @Test
        void stopsAtFirstViolation() {
            FraudRule expensiveRule = org.mockito.Mockito.mock(FraudRule.class);
            var engine = new FraudEngine(List.of(new AmountLimitRule(LIMITS), expensiveRule));

            var result = engine.check(transfer("99999.00"));

            assertThat(result).isInstanceOf(FraudCheckResult.Violation.class);
            verify(expensiveRule, never()).evaluate(any());
        }

        @Test
        void passesWhenAllRulesPass() {
            var engine = new FraudEngine(List.of(new AmountLimitRule(LIMITS), t -> FraudCheckResult.PASSED));

            assertThat(engine.check(transfer("10.00"))).isEqualTo(FraudCheckResult.PASSED);
        }
    }
}
