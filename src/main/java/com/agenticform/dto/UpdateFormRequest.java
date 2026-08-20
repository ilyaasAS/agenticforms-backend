package com.agenticform.dto;

import java.util.List;

import com.agenticform.model.entity.FormStatus;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

public record UpdateFormRequest(
        @Size(max = 255, message = "Title must be at most 255 characters")
        String title,

        @Size(max = 10000, message = "Description must be at most 10000 characters")
        String description,

        FormStatus status,

        @Valid
        List<LogicRuleDto> logicRules,

        @Valid
        List<CalculationDto> calculations,

        @Valid
        List<FormPageDto> pages,

        @Size(max = 32, message = "themeId must be at most 32 characters")
        String themeId,

        @Valid
        ProgressBarConfigDto progressBar
) {
}
