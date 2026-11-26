package com.bankplatform.search.search;

import com.bankplatform.common.event.CurrencyCode;
import com.bankplatform.common.event.TransactionStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;

/** Query parameters of the search endpoint - all optional and combined with AND. */
public record SearchCriteria(
        @Schema(description = "Account number (source or target)") @Size(max = 34) String account,
        TransactionStatus status,
        CurrencyCode currency,
        @DecimalMin("0") BigDecimal minAmount,
        @DecimalMin("0") BigDecimal maxAmount,
        @Schema(description = "ISO-8601 instant, inclusive", example = "2026-01-01T00:00:00Z") Instant from,
        @Schema(description = "ISO-8601 instant, exclusive", example = "2027-01-01T00:00:00Z") Instant to,
        @Schema(description = "Free text over description, rejection detail and account numbers")
        @Size(max = 200) String q,
        @Min(0) Integer page,
        @Min(1) @Max(100) Integer size
) {

    public int pageOrDefault() {
        return page != null ? page : 0;
    }

    public int sizeOrDefault() {
        return size != null ? size : 20;
    }

    @AssertTrue(message = "minAmount must not be greater than maxAmount")
    @Schema(hidden = true)
    public boolean isAmountRangeValid() {
        return minAmount == null || maxAmount == null || minAmount.compareTo(maxAmount) <= 0;
    }

    @AssertTrue(message = "from must be before to")
    @Schema(hidden = true)
    public boolean isDateRangeValid() {
        return from == null || to == null || from.isBefore(to);
    }
}
