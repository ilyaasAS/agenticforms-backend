package com.agenticform.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AdminCreateUserRequest(
        @NotBlank(message = "Email is required")
        @Email(message = "Email format is invalid")
        @Size(max = 255)
        String email,

        @NotBlank(message = "Password is required")
        @Size(min = 8, max = 255)
        @Pattern(
                regexp = "^(?=.*[A-Z])(?=.*[\\d\\W_]).{8,255}$",
                message = "Password must include an uppercase letter and a digit or special character"
        )
        String password,

        @Size(max = 255)
        String firstName,

        @Size(max = 255)
        String lastName,

        @Pattern(regexp = "ROLE_USER|ROLE_ADMIN", message = "Rôle invalide (ROLE_USER ou ROLE_ADMIN).")
        String role
) {
}
