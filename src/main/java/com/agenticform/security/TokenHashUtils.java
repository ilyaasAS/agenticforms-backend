package com.agenticform.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Hachage SHA-256 pour tokens one-shot (reset password, futurs verify-email).
 */
public final class TokenHashUtils {

    private TokenHashUtils() {
    }

    public static String sha256Hex(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("raw token required");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }
}
