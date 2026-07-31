package com.agenticform.dto;

import java.time.Instant;
import java.util.List;

public record FormFieldResponse(
        Long id,
        Long formId,
        String label,
        String fieldType,
        boolean required,
        int displayOrder,
        List<String> options,
        String placeholder,
        String uiComponent,
        FieldSettingsDto settings,
        Instant createdAt,
        Instant updatedAt
) {
}
