package com.bankplatform.processing.fraud;

import com.bankplatform.common.event.RejectionReason;

/** Outcome of a fraud rule. Sealed: a result is either a pass or a violation, nothing else. */
public sealed interface FraudCheckResult {

    FraudCheckResult PASSED = new Passed();

    record Passed() implements FraudCheckResult {
    }

    record Violation(RejectionReason reason, String detail) implements FraudCheckResult {
    }
}
