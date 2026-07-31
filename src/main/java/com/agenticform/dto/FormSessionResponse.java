package com.agenticform.dto;

import java.time.Instant;
import java.util.Map;

public record FormSessionResponse(
        String sessionId,
        Long formId,
        Long lastFieldId,
        String status,
        Map<Long, String> answers,
        Instant createdAt,
        Instant updatedAt
) {
}
