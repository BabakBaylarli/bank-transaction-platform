package com.bankplatform.common.event;

public enum TransactionStatus {
    PENDING, COMPLETED, REJECTED;

    public boolean isFinal() {
        return this != PENDING;
    }
}
