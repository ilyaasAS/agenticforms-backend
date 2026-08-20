package com.agenticform.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record GuestInvite(
        @NotBlank @Email String email,
        String name) {
}
