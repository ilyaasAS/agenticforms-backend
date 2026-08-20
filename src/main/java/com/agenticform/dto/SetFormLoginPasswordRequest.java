package com.agenticform.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SetFormLoginPasswordRequest(
        @NotBlank @Size(min = 1, max = 128) String password) {
}
