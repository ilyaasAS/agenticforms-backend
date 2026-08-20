package com.agenticform.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record FormLoginResumeStatusRequest(
        @NotBlank @Email @Size(max = 320) String email) {
}
