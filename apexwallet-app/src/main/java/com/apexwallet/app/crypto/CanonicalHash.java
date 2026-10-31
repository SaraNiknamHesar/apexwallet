package com.apexwallet.app.crypto;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;

/** Explicit field order. Raw JSON is not hashed, because key order and spaces would change the digest. */
public final class CanonicalHash {

    private CanonicalHash() {
    }

    public static String transfer(UUID from, UUID to, long amountMinor, String currency, String description) {
        String canonical = from + "|" + to + "|" + amountMinor + "|" + currency + "|" + (description == null ? "" : description.trim());
        return sha256(canonical);
    }

    public static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
