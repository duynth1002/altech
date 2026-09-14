package com.example.walletledger.wallet.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

public final class RequestHashCalculator {

    private RequestHashCalculator() {
    }

    public static String hash(
            String operation,
            UUID playerId,
            long amount,
            String reason,
            String referenceType,
            String referenceId) {
        String canonical = operation
                + "|" + playerId
                + "|" + amount
                + "|" + reason
                + "|" + emptyIfNull(referenceType)
                + "|" + emptyIfNull(referenceId);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is required", ex);
        }
    }

    private static String emptyIfNull(String value) {
        return value == null ? "" : value;
    }
}
