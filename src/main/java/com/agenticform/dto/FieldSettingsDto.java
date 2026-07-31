package com.agenticform.dto;

/**
 * Paramètres UI Fillout-like pour un champ (sérialisés en settings_json).
 */
public record FieldSettingsDto(
        String caption,
        String defaultValue,
        Boolean halfWidth,
        Boolean hideAlways,
        String hideMode,
        Integer minLength,
        Integer maxLength,
        String validationPattern,
        String validationRegex,
        String errorMessage
) {
}
