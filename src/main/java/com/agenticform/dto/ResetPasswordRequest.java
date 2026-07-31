package com.agenticform.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
        @NotBlank(message = "Token is required")
        @Size(max = 64, message = "Token must be at most 64 characters")
        String token,

        @NotBlank(message = "Password is required")
        @Size(min = 8, max = 255, message = "Password must be between 8 and 255 characters")
        @Pattern(
                regexp = "^(?=.*[A-Z])(?=.*[\\d\\W_]).{8,255}$",
                message = "Password must include an uppercase letter and a digit or special character"
        )
        String newPassword
) {
}
