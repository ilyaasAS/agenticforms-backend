package com.agenticform.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LogicConditionDto(
        @NotBlank
        @Size(max = 64)
        String fieldId,

        @NotBlank
        @Size(max = 32)
        String operator,

        @Size(max = 2000)
        String value
) {
}
