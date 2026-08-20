package com.agenticform.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SubmitFormRequest(
        @NotNull @Valid List<SubmissionAnswerRequest> answers,
        @Size(max = 64) String sessionId,
        java.util.Map<String, String> urlParams,
        @Email @Size(max = 320) String respondentEmail
) {
}
