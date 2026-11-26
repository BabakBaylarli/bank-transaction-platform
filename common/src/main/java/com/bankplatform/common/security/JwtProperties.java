package com.bankplatform.common.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * @param secret HMAC-SHA256 key (at least 32 characters), always supplied through the environment
 */
@Validated
@ConfigurationProperties("app.security.jwt")
public record JwtProperties(
        @NotBlank @Size(min = 32, message = "must be at least 32 characters") String secret,
        @NotBlank @DefaultValue("bank-transaction-platform") String issuer,
        @NotNull @DefaultValue("PT1H") Duration ttl
) {
}
