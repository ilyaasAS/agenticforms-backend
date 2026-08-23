package com.agenticform.dto;

import jakarta.validation.constraints.Pattern;

public record AdminUpdateUserRequest(
        @Pattern(regexp = "ROLE_USER|ROLE_ADMIN", message = "Rôle invalide (ROLE_USER ou ROLE_ADMIN).")
        String role,
        Boolean blocked
) {
}
