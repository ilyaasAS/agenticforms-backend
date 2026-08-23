package com.agenticform.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Changement / définition de mot de passe (compte connecté).
 * {@code currentPassword} obligatoire si le login mot de passe est déjà actif.
 */
public record ChangePasswordRequest(
        @Size(max = 255, message = "Le mot de passe actuel est trop long.")
        String currentPassword,

        @NotBlank(message = "Le nouveau mot de passe est obligatoire.")
        @Size(min = 8, max = 255, message = "Le mot de passe doit contenir entre 8 et 255 caractères.")
        @Pattern(
                regexp = "^(?=.*[A-Z])(?=.*[\\d\\W_]).{8,255}$",
                message = "Le mot de passe doit inclure une majuscule et un chiffre ou caractère spécial."
        )
        String newPassword
) {
}
