package com.bankplatform.search.search;

import com.bankplatform.common.event.CurrencyCode;
import com.bankplatform.common.event.RejectionReason;
import com.bankplatform.common.event.TransactionStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record TransactionSearchHit(
        String id,
        String sourceAccount,
        String targetAccount,
        BigDecimal amount,
        CurrencyCode currency,
        TransactionStatus status,
        RejectionReason rejectionReason,
        String rejectionDetail,
        String description,
        Instant createdAt,
        Instant updatedAt
) {
}
