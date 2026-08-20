package com.agenticform.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record FormLoginSendCodeRequest(
        @NotBlank @Email String email) {
}
