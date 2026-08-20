package com.agenticform.dto;

import java.time.Instant;
import java.util.List;

public record PublicFormResponse(
        Long id,
        String title,
        String description,
        String status,
        List<PublicFormFieldResponse> fields,
        List<LogicRuleDto> logicRules,
        List<CalculationDto> calculations,
        List<FormPageDto> pages,
        String themeId,
        ProgressBarConfigDto progressBar,
        Instant updatedAt
) {
}
