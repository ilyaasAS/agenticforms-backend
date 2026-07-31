package com.agenticform.dto;

import jakarta.validation.constraints.NotNull;

public record SubmissionAnswerRequest(
        @NotNull Long fieldId,
        String value
) {
}
