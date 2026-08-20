package com.agenticform.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record FormLoginPasswordVerifyRequest(
        @NotBlank @Size(min = 1, max = 128) String password) {
}
