package com.agenticform.dto;

import java.time.LocalDateTime;

public record WorkspaceResponse(
        Long id,
        String name,
        String slug,
        String description,
        Long ownerId,
        String myRole,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
