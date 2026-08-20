package com.agenticform.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@JsonIgnoreProperties(ignoreUnknown = true)
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
        List<LogicConditionDto> conditions,

        /** Conditions Fillout (groupes AND/OR + refs URL/contact/champs). */
        @Valid
        VisibilityNodeDto logic,

        /** Branche DEFAULT (fallback) depuis la page source. */
        Boolean isDefault
) {
}
