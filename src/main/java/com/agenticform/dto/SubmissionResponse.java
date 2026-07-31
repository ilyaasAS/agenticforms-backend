package com.agenticform.dto;

import java.time.Instant;

public record SubmissionResponse(
        Long id,
        Long formId,
        Instant submittedAt
) {
}
