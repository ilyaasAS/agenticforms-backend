package com.agenticform.dto;

import java.time.Instant;
import java.util.Map;

public record InProgressSessionResponse(
        String sessionId,
        Long formId,
        Long lastFieldId,
        String lastFieldLabel,
        int currentStep,
        int totalSteps,
        double progressPercent,
        Map<Long, String> answers,
        String respondentEmail,
        Instant updatedAt,
        Instant createdAt
) {
}
