package com.agenticform.dto;

import java.util.List;

public record FormResultsFieldResponse(
        Long id,
        String label,
        String fieldType,
        int displayOrder,
        List<String> options,
        /** true si le champ a été retiré du formulaire mais conserve des réponses. */
        boolean removed,
        FieldSettingsDto settings
) {
}
