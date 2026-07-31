package com.agenticform.dto;

import java.time.LocalDateTime;

public record WorkspaceSummaryResponse(
        Long id,
        String name,
        String slug,
        String description,
        String myRole,
        LocalDateTime createdAt
) {
}
