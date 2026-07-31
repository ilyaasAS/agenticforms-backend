package com.agenticform.dto;

import java.time.Instant;
import java.util.Map;

public record FormSubmissionRowResponse(
        Long id,
        Instant submittedAt,
        Map<Long, String> answers
) {
}
