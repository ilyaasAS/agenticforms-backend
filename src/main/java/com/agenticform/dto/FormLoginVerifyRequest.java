package com.agenticform.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record FormLoginVerifyRequest(
        @NotBlank @Email String email,
        @NotBlank @Size(min = 4, max = 12) String code) {
}
