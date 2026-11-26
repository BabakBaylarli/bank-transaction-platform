package com.bankplatform.transaction.transfer.dto;

import com.bankplatform.common.event.CurrencyCode;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CreateTransferRequest(
        @NotBlank @Size(max = 34) String sourceAccount,
        @NotBlank @Size(max = 34) String targetAccount,
        @NotNull @DecimalMin("0.01") @Digits(integer = 15, fraction = 2) BigDecimal amount,
        @NotNull CurrencyCode currency,
        @Size(max = 255) String description
) {
}
