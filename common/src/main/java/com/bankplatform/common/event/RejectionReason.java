package com.bankplatform.common.event;

public enum RejectionReason {
    AMOUNT_LIMIT_EXCEEDED,
    DAILY_LIMIT_EXCEEDED,
    VELOCITY_LIMIT_EXCEEDED,
    INSUFFICIENT_FUNDS,
    ACCOUNT_NOT_FOUND,
    CURRENCY_MISMATCH
}
