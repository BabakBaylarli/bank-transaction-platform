package com.bankplatform.processing.ledger;

import com.bankplatform.common.event.CurrencyCode;
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
import java.time.Instant;
import java.util.UUID;

/**
 * Processing-service's view of an account: just what is needed to move money.
 * The table is owned (created/migrated) by transaction-service; {@link Version} gives optimistic
 * locking, so two concurrent debits can never both succeed on a stale balance.
 */
@Entity
@Table(name = "accounts", schema = "public")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LedgerAccount {

    @Id
    private UUID id;

    @Column(nullable = false, updatable = false)
    private String number;

    @Column(name = "owner_id", nullable = false, updatable = false)
    private UUID ownerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private CurrencyCode currency;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal balance;

    @Version
    private long version;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public LedgerAccount(UUID id, String number, UUID ownerId, CurrencyCode currency, BigDecimal balance) {
        this.id = id;
        this.number = number;
        this.ownerId = ownerId;
        this.currency = currency;
        this.balance = balance;
        this.updatedAt = Instant.now();
    }

    public boolean canDebit(BigDecimal amount) {
        return balance.compareTo(amount) >= 0;
    }

    public void debit(BigDecimal amount, Instant now) {
        if (!canDebit(amount)) {
            throw new IllegalStateException("Insufficient funds on " + number);
        }
        balance = balance.subtract(amount);
        updatedAt = now;
    }

    public void credit(BigDecimal amount, Instant now) {
        balance = balance.add(amount);
        updatedAt = now;
    }
}
