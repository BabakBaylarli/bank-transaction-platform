package com.bankplatform.processing.fraud;

import com.bankplatform.common.event.TransactionCreatedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/** Runs all rules in {@code @Order} and stops at the first violation. */
@Component
@RequiredArgsConstructor
public class FraudEngine {

    private final List<FraudRule> rules;

    public FraudCheckResult check(TransactionCreatedEvent transfer) {
        for (FraudRule rule : rules) {
            FraudCheckResult result = rule.evaluate(transfer);
            if (result instanceof FraudCheckResult.Violation) {
                return result;
            }
        }
        return FraudCheckResult.PASSED;
    }
}
