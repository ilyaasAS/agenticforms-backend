package com.agenticform.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@JsonIgnoreProperties(ignoreUnknown = true)
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
