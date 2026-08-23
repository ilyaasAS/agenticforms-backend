package com.agenticform.security;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class JwtTokenProviderSecretValidationTest {

    @Test
    void prodRejectsBlankSecret() {
        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> JwtTokenProvider.validateSecretForProfiles("  ", "prod"));
        assertTrue(ex.getMessage().contains("JWT_SECRET"));
    }

    @Test
    void prodRejectsShortSecret() {
        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> JwtTokenProvider.validateSecretForProfiles("short-secret", "prod"));
        assertTrue(ex.getMessage().contains("32"));
        assertTrue(ex.getMessage().contains(String.valueOf("short-secret".length())));
    }

    @Test
    void prodAcceptsSecretOfAtLeast32Chars() {
        String secret = "a".repeat(JwtTokenProvider.MIN_SECRET_LENGTH);
        assertDoesNotThrow(() -> JwtTokenProvider.validateSecretForProfiles(secret, "prod"));
    }

    @Test
    void nonProdAllowsShortSecretAtValidationLayer() {
        assertDoesNotThrow(() -> JwtTokenProvider.validateSecretForProfiles("short", "dev"));
        assertDoesNotThrow(() -> JwtTokenProvider.validateSecretForProfiles("short", "test"));
    }
}
