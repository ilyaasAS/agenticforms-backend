package com.agenticform.dto;

import java.time.Instant;

public record AdminFormResponse(
        Long id,
        String title,
        String status,
        boolean blocked,
        Long workspaceId,
        String workspaceName,
        Long ownerId,
        String ownerEmail,
        String ownerName,
        Instant createdAt,
        Instant updatedAt
) {
}
