package com.agenticform.dto;

/**
 * Résultat interne login/register/oauth : JWT pour le cookie + payload JSON sans token.
 */
public record AuthSessionResult(
        String accessToken,
        AuthResponse response
) {
}
