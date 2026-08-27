package com.agenticform.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import com.agenticform.model.entity.Role;
import com.agenticform.model.entity.User;

class JwtTokenProviderTest {

    private JwtTokenProvider provider;

    @BeforeEach
    void setUp() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("test");
        provider = new JwtTokenProvider(
                "test-jwt-secret-key-32chars-min!!",
                3_600_000L,
                env);
    }

    @Test
    void generateAndValidateTokenRoundTrip() {
        User user = new User();
        user.setId(99L);
        user.setEmail("ada@example.com");
        user.setRole(Role.ROLE_USER);
        user.setTokenVersion(3);

        String token = provider.generateToken(user);

        assertTrue(provider.validateToken(token));
        assertEquals(99L, provider.getUserIdFromToken(token));
        assertEquals(3, provider.getTokenVersionFromToken(token));
    }

    @Test
    void validateTokenRejectsGarbage() {
        assertFalse(provider.validateToken("not.a.jwt"));
        assertFalse(provider.validateToken(""));
    }

    @Test
    void prodProfileRequiresLongSecret() {
        assertThrows(IllegalStateException.class,
                () -> JwtTokenProvider.validateSecretForProfiles("short", "prod"));
        JwtTokenProvider.validateSecretForProfiles("test-jwt-secret-key-32chars-min!!", "prod");
    }
}
