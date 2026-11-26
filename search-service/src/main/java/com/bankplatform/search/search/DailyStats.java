package com.bankplatform.search.search;

import com.bankplatform.common.event.TransactionStatus;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One row of the aggregation: totals for a (day, status, currency) bucket. */
public record DailyStats(LocalDate day, TransactionStatus status, String currency, long count, BigDecimal totalAmount) {
}
