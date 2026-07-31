package com.agenticform.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CalculationDto(
        @NotBlank
        @Size(max = 64)
        String id,

        @NotBlank
        @Size(max = 255)
        String name,

        @NotBlank
        @Size(max = 32)
        String type,

        @Size(max = 2000)
        String initialValue,

        @Valid
        List<CalculationRuleDto> rules
) {
}
