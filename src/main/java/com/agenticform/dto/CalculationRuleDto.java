package com.agenticform.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CalculationRuleDto(
        @NotBlank
        @Size(max = 64)
        String id,

        @Size(max = 255)
        String label,

        @Valid
        List<LogicConditionDto> conditions,

        @Valid
        VisibilityNodeDto logic,

        @Size(max = 32)
        String operation,

        Boolean always,

        @Size(max = 2000)
        String resultValue
) {
}
