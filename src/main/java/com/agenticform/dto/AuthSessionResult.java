package com.agenticform.dto;

/**
 * Résultat interne login/register/oauth : JWT pour le cookie + payload JSON sans token.
 * {@code expiresInMs} aligne Max-Age du cookie sur l'expiration du JWT.
 */
public record AuthSessionResult(
        String accessToken,
        AuthResponse response,
        long expiresInMs
) {
}
