package com.agenticform.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ContactReplyRequest(
        @NotBlank(message = "Le message de réponse est obligatoire.")
        @Size(max = 5000, message = "La réponse ne peut pas dépasser 5000 caractères.")
        String body
) {
}
