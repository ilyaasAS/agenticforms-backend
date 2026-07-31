package com.agenticform.dto;

/** Réponse auth JSON — le JWT n'est jamais exposé ici (cookie HttpOnly AF_ACCESS). */
public record AuthResponse(
        UserInfo user
) {
    public record UserInfo(
            Long id,
            String email,
            String role,
            String fullName
    ) {
    }
}
