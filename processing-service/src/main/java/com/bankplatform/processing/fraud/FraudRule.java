package com.bankplatform.processing.fraud;

import com.bankplatform.common.event.TransactionCreatedEvent;

public interface FraudRule {

    FraudCheckResult evaluate(TransactionCreatedEvent transfer);
}
