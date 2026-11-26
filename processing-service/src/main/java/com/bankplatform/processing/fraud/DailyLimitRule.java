package com.bankplatform.processing.fraud;

import com.bankplatform.common.event.RejectionReason;
import com.bankplatform.common.event.TransactionCreatedEvent;
import com.bankplatform.processing.ledger.ProcessedTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

/** Sum of completed outgoing transfers of the account today (UTC) plus this one must stay within the limit. */
@Order(3)
@Component
@RequiredArgsConstructor
public class DailyLimitRule implements FraudRule {

    private final ProcessedTransactionRepository repository;
    private final FraudProperties properties;
    private final Clock clock;

    @Override
    public FraudCheckResult evaluate(TransactionCreatedEvent transfer) {
        Instant startOfDay = LocalDate.now(clock).atStartOfDay(ZoneOffset.UTC).toInstant();
        BigDecimal spentToday = repository.sumCompletedOutgoingSince(transfer.sourceAccount(), startOfDay);
        BigDecimal total = spentToday.add(transfer.amount());
        if (total.compareTo(properties.dailyLimit()) > 0) {
            return new FraudCheckResult.Violation(RejectionReason.DAILY_LIMIT_EXCEEDED,
                    "Daily limit of %s exceeded (already sent today: %s)"
                            .formatted(properties.dailyLimit().toPlainString(), spentToday.toPlainString()));
        }
        return FraudCheckResult.PASSED;
    }
}
