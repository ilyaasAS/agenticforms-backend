package com.agenticform.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@JsonIgnoreProperties(ignoreUnknown = true)
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
        List<CalculationRuleDto> rules,

        @Size(max = 64)
        String startFieldId,

        @Size(max = 32)
        String startRefKind,

        @Size(max = 64)
        String endFieldId,

        @Size(max = 32)
        String endRefKind,

        @Size(max = 32)
        String units
) {
}
