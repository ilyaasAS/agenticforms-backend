package com.agenticform.dto;

import java.util.List;

/**
 * Configuration globale de la barre de progression (formulaire).
 * Persistée dans pages_json (document { pages, progressBar }).
 */
public record ProgressBarConfigDto(
        Boolean enabled,
        String color,
        List<ProgressBarStepDto> steps
) {
}
