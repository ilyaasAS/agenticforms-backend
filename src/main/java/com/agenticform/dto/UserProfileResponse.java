package com.agenticform.dto;

import java.time.LocalDateTime;
import java.util.List;

public record UserProfileResponse(
        Long id,
        String email,
        String role,
        String fullName,
        LocalDateTime createdAt,
        boolean passwordEnabled,
        List<String> linkedProviders
) {
}
