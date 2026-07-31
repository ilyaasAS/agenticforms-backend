package com.agenticform.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record OAuthExchangeRequest(
        @NotBlank(message = "OAuth code is required")
        @Size(max = 128, message = "OAuth code must be at most 128 characters")
        String code
) {
}
