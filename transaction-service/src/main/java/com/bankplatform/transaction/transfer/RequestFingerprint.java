package com.bankplatform.transaction.transfer;

import com.bankplatform.transaction.transfer.dto.CreateTransferRequest;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/** Canonical hash of a transfer request - equal requests produce equal fingerprints. */
final class RequestFingerprint {

    private RequestFingerprint() {
    }

    static String of(CreateTransferRequest request) {
        String canonical = String.join("|",
                request.sourceAccount(),
                request.targetAccount(),
                request.amount().stripTrailingZeros().toPlainString(),
                request.currency().name(),
                Objects.requireNonNullElse(request.description(), ""));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
