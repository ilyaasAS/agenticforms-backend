package com.agenticform.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CalculationRuleDto(
        @NotBlank
        @Size(max = 64)
        String id,

        @Size(max = 255)
        String label,

        @Valid
        List<LogicConditionDto> conditions,

        @Size(max = 2000)
        String resultValue
) {
}
