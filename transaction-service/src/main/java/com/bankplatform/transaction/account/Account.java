package com.bankplatform.transaction.account;

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
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

/**
 * A customer account. The balance is <b>only</b> changed by processing-service; this service
 * creates accounts and reads balances. {@link Version} protects against lost updates.
 */
@Entity
@Table(name = "accounts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Account {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 34)
    private String number;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 3)
    private CurrencyCode currency;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal balance;

    @Version
    private long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Account(String number, UUID ownerId, CurrencyCode currency, BigDecimal openingBalance, Instant now) {
        this.id = UUID.randomUUID();
        this.number = number;
        this.ownerId = ownerId;
        this.currency = currency;
        this.balance = openingBalance.setScale(2, RoundingMode.UNNECESSARY);
        this.createdAt = now;
        this.updatedAt = now;
    }

    public boolean isOwnedBy(UUID userId) {
        return ownerId.equals(userId);
    }
}
