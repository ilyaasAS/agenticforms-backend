package com.agenticform.dto;

/**
 * Une étape de la barre de progression (libellé affiché).
 */
public record ProgressBarStepDto(
        String id,
        String label
) {
}
