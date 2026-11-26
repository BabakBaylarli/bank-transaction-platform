package com.bankplatform.processing.ledger;

import com.bankplatform.common.event.CurrencyCode;
import com.bankplatform.common.event.RejectionReason;
import com.bankplatform.common.event.TransactionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One row per processed transfer, written in the same DB transaction as the balance change.
 * It makes the consumer idempotent (redelivered events are recognised) and feeds the fraud rules.
 */
@Entity
@Table(name = "processed_transactions", schema = "processing")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProcessedTransaction {

    @Id
    @Column(name = "transaction_id")
    private UUID transactionId;

    @Column(name = "source_account", nullable = false)
    private String sourceAccount;

    @Column(name = "target_account", nullable = false)
    private String targetAccount;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CurrencyCode currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "rejection_reason")
    private RejectionReason rejectionReason;

    @Column(name = "rejection_detail")
    private String rejectionDetail;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    public ProcessedTransaction(UUID transactionId, String sourceAccount, String targetAccount, BigDecimal amount,
                                CurrencyCode currency, TransactionStatus status, RejectionReason rejectionReason,
                                String rejectionDetail, Instant processedAt) {
        this.transactionId = transactionId;
        this.sourceAccount = sourceAccount;
        this.targetAccount = targetAccount;
        this.amount = amount;
        this.currency = currency;
        this.status = status;
        this.rejectionReason = rejectionReason;
        this.rejectionDetail = rejectionDetail;
        this.processedAt = processedAt;
    }
}
