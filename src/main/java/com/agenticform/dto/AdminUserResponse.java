package com.agenticform.dto;

import java.time.LocalDateTime;

public record AdminUserResponse(
        Long id,
        String email,
        String fullName,
        String role,
        boolean blocked,
        boolean emailVerified,
        LocalDateTime createdAt
) {
}
