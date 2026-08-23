package com.agenticform.dto;

import java.time.Instant;

public record ContactMessageResponse(
        String id,
        String name,
        String email,
        String subject,
        String message,
        Instant createdAt,
        String status
) {
}
