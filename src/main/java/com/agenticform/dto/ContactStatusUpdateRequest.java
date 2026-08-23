package com.agenticform.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ContactStatusUpdateRequest(
        @NotBlank(message = "Le statut est obligatoire.")
        @Pattern(regexp = "NEW|READ|ARCHIVED", message = "Statut invalide (NEW, READ ou ARCHIVED).")
        String status
) {
}
