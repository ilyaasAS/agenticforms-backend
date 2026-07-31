package com.agenticform.dto;

import java.time.Instant;

public record FormSummaryResponse(
        Long id,
        Long workspaceId,
        String title,
        String description,
        String status,
        int fieldCount,
        Long createdById,
        Instant createdAt,
        Instant updatedAt
) {
}
