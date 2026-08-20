package com.agenticform.dto;

import java.util.List;

/**
 * Colonne d’un champ TABLE (sérialisée dans settings_json).
 */
public record TableColumnDto(
        String id,
        String label,
        String columnType,
        List<String> options,
        Integer width
) {
}
