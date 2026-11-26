package com.bankplatform.processing.fraud;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;

/**
 * Fraud limits. For simplicity limits are expressed in the transfer's own currency
 * (a real system would normalise to a base currency).
 */
@Validated
@ConfigurationProperties("app.fraud")
public record FraudProperties(
        @NotNull @Positive @DefaultValue("10000.00") BigDecimal maxSingleAmount,
        @NotNull @Positive @DefaultValue("20000.00") BigDecimal dailyLimit,
        @Min(1) @DefaultValue("5") int maxTransactionsPerMinute
) {
}
