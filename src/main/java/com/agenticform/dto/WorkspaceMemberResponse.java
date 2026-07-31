package com.agenticform.dto;

import java.time.LocalDateTime;

public record WorkspaceMemberResponse(
        Long id,
        Long userId,
        String email,
        String fullName,
        String role,
        LocalDateTime joinedAt
) {
}
