package com.agenticform.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LogicRuleDto(
        @NotBlank
        @Size(max = 64)
        String id,

        @NotBlank
        @Size(max = 64)
        String sourcePageId,

        @NotBlank
        @Size(max = 64)
        String targetPageId,

        @Valid
        List<LogicConditionDto> conditions
) {
}
