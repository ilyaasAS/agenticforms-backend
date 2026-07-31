package com.agenticform.dto;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;

public record ReorderFormFieldsRequest(
        @NotEmpty(message = "Field ids are required")
        List<Long> fieldIds
) {
}
