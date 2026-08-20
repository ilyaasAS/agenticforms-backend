package com.agenticform.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpsertFormSessionRequest(
        @NotBlank @Size(max = 64) String sessionId,
        Long lastFieldId,
        @Valid List<SubmissionAnswerRequest> answers,
        String status,
        @Email @Size(max = 320) String respondentEmail
) {
}
