package com.bankplatform.processing.api;

import com.bankplatform.common.event.CurrencyCode;
import com.bankplatform.common.event.RejectionReason;
import com.bankplatform.common.event.TransactionStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ProcessedTransactionResponse(
        UUID transactionId,
        String sourceAccount,
        String targetAccount,
        BigDecimal amount,
        CurrencyCode currency,
        TransactionStatus status,
        RejectionReason rejectionReason,
        String rejectionDetail,
        Instant processedAt
) {
}
