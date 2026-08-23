package com.agenticform.dto;

import jakarta.validation.constraints.NotNull;

public record AdminFormBlockRequest(
        @NotNull(message = "blocked is required")
        Boolean blocked
) {
}
