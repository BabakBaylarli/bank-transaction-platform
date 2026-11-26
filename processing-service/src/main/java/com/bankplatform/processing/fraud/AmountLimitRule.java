package com.bankplatform.processing.fraud;

import com.bankplatform.common.event.RejectionReason;
import com.bankplatform.common.event.TransactionCreatedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** A single transfer may not exceed the configured amount. Cheapest rule, evaluated first. */
@Order(1)
@Component
@RequiredArgsConstructor
public class AmountLimitRule implements FraudRule {

    private final FraudProperties properties;

    @Override
    public FraudCheckResult evaluate(TransactionCreatedEvent transfer) {
        if (transfer.amount().compareTo(properties.maxSingleAmount()) > 0) {
            return new FraudCheckResult.Violation(RejectionReason.AMOUNT_LIMIT_EXCEEDED,
                    "Amount %s exceeds the single-transfer limit of %s"
                            .formatted(transfer.amount().toPlainString(), properties.maxSingleAmount().toPlainString()));
        }
        return FraudCheckResult.PASSED;
    }
}
