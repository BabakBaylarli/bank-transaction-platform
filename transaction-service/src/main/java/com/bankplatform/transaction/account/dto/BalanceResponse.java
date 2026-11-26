package com.bankplatform.transaction.account.dto;

import com.bankplatform.common.event.CurrencyCode;

import java.math.BigDecimal;
import java.time.Instant;

public record BalanceResponse(String accountNumber, BigDecimal balance, CurrencyCode currency, Instant asOf) {
}
