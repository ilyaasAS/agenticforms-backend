package com.agenticform.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Confirmation de suppression de compte : l'e-mail doit correspondre au compte connecté. */
public record DeleteAccountRequest(
        @NotBlank(message = "Confirmez votre e-mail pour supprimer le compte.")
        @Email(message = "Le format de l'e-mail est invalide.")
        @Size(max = 255, message = "L'e-mail ne peut pas dépasser 255 caractères.")
        String confirmEmail
) {
}
