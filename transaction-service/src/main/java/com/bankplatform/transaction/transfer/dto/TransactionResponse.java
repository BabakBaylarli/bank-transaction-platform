package com.bankplatform.transaction.transfer.dto;

import com.bankplatform.common.event.CurrencyCode;
import com.bankplatform.common.event.RejectionReason;
import com.bankplatform.common.event.TransactionStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TransactionResponse(
        UUID id,
        String sourceAccount,
        String targetAccount,
        BigDecimal amount,
        CurrencyCode currency,
        String description,
        TransactionStatus status,
        RejectionReason rejectionReason,
        String rejectionDetail,
        Instant createdAt,
        Instant updatedAt
) {
}
