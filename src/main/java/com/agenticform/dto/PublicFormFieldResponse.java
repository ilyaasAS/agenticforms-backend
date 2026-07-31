package com.agenticform.dto;

import java.util.List;

public record PublicFormFieldResponse(
        Long id,
        String label,
        String fieldType,
        boolean required,
        int displayOrder,
        List<String> options,
        String placeholder,
        String uiComponent
) {
}
