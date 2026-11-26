package com.bankplatform.transaction.transfer;

import com.bankplatform.common.event.CurrencyCode;
import com.bankplatform.common.event.RejectionReason;
import com.bankplatform.common.event.TransactionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "transactions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Transaction {

    @Id
    private UUID id;

    @Column(name = "initiator_id", nullable = false)
    private UUID initiatorId;

    @Column(name = "idempotency_key", nullable = false, length = 100)
    private String idempotencyKey;

    /** SHA-256 of the request body, to detect a key being reused for a different request. */
    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @Column(name = "source_account", nullable = false, length = 34)
    private String sourceAccount;

    @Column(name = "target_account", nullable = false, length = 34)
    private String targetAccount;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 3)
    private CurrencyCode currency;

    @Column(length = 255)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransactionStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "rejection_reason", length = 40)
    private RejectionReason rejectionReason;

    @Column(name = "rejection_detail", length = 255)
    private String rejectionDetail;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    public Transaction(UUID initiatorId, String idempotencyKey, String requestHash, String sourceAccount,
                       String targetAccount, BigDecimal amount, CurrencyCode currency, String description,
                       Instant now) {
        this.id = UUID.randomUUID();
        this.initiatorId = initiatorId;
        this.idempotencyKey = idempotencyKey;
        this.requestHash = requestHash;
        this.sourceAccount = sourceAccount;
        this.targetAccount = targetAccount;
        this.amount = amount.setScale(2, RoundingMode.UNNECESSARY);
        this.currency = currency;
        this.description = description;
        this.status = TransactionStatus.PENDING;
        this.createdAt = now;
        this.updatedAt = now;
    }

    /** @return false when the transaction already reached a final state (duplicate event) */
    public boolean complete(Instant now) {
        if (status.isFinal()) {
            return false;
        }
        this.status = TransactionStatus.COMPLETED;
        this.updatedAt = now;
        return true;
    }

    public boolean reject(RejectionReason reason, String detail, Instant now) {
        if (status.isFinal()) {
            return false;
        }
        this.status = TransactionStatus.REJECTED;
        this.rejectionReason = reason;
        this.rejectionDetail = detail;
        this.updatedAt = now;
        return true;
    }
}
