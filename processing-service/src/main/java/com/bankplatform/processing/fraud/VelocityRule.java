package com.bankplatform.processing.fraud;

import com.bankplatform.common.event.RejectionReason;
import com.bankplatform.common.event.TransactionCreatedEvent;
import com.bankplatform.processing.ledger.ProcessedTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;

/** Too many transfers from one account within a minute is a classic account-takeover signal. */
@Order(2)
@Component
@RequiredArgsConstructor
public class VelocityRule implements FraudRule {

    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final ProcessedTransactionRepository repository;
    private final FraudProperties properties;
    private final Clock clock;

    @Override
    public FraudCheckResult evaluate(TransactionCreatedEvent transfer) {
        long recent = repository.countBySourceAccountAndProcessedAtAfter(transfer.sourceAccount(),
                clock.instant().minus(WINDOW));
        if (recent >= properties.maxTransactionsPerMinute()) {
            return new FraudCheckResult.Violation(RejectionReason.VELOCITY_LIMIT_EXCEEDED,
                    "More than %d transfers per minute from this account".formatted(properties.maxTransactionsPerMinute()));
        }
        return FraudCheckResult.PASSED;
    }
}
