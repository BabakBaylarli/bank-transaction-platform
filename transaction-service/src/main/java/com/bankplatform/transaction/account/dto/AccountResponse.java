package com.bankplatform.transaction.account.dto;

import com.bankplatform.common.event.CurrencyCode;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AccountResponse(UUID id, String number, CurrencyCode currency, BigDecimal balance, Instant createdAt) {
}
