package com.agenticform.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ContactRequest(
        @NotBlank(message = "Le nom est obligatoire.")
        @Size(max = 120, message = "Le nom ne peut pas dépasser 120 caractères.")
        String name,

        @NotBlank(message = "L'e-mail est obligatoire.")
        @Email(message = "Le format de l'e-mail est invalide.")
        @Size(max = 255, message = "L'e-mail ne peut pas dépasser 255 caractères.")
        String email,

        @NotBlank(message = "L'objet est obligatoire.")
        @Size(max = 200, message = "L'objet ne peut pas dépasser 200 caractères.")
        String subject,

        @NotBlank(message = "Le message est obligatoire.")
        @Size(max = 5000, message = "Le message ne peut pas dépasser 5000 caractères.")
        String message
) {
}
