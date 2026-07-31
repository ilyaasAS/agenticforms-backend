package com.agenticform.dto;

import java.util.List;

import com.agenticform.model.entity.FieldType;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateFormFieldRequest(
        @NotBlank(message = "Label is required")
        @Size(max = 255, message = "Label must be at most 255 characters")
        String label,

        @NotNull(message = "Field type is required")
        FieldType fieldType,

        Boolean required,

        Integer displayOrder,

        List<@Size(max = 255) String> options,

        @Size(max = 255, message = "Placeholder must be at most 255 characters")
        String placeholder,

        @Size(max = 64, message = "uiComponent must be at most 64 characters")
        String uiComponent,

        @Valid
        FieldSettingsDto settings
) {
}
