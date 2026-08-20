package com.agenticform.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record FormLoginAbandonProgressRequest(
        @NotBlank @Email @Size(max = 320) String email) {
}
