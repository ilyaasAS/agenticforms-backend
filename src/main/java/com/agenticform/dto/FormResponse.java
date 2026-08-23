package com.agenticform.dto;

import java.time.Instant;
import java.util.List;

public record FormResponse(
        Long id,
        Long workspaceId,
        String title,
        String description,
        String status,
        Long createdById,
        List<FormFieldResponse> fields,
        List<LogicRuleDto> logicRules,
        List<CalculationDto> calculations,
        List<FormPageDto> pages,
        String themeId,
        ProgressBarConfigDto progressBar,
        Instant createdAt,
        Instant updatedAt,
        Instant publishedAt,
        boolean hasUnpublishedChanges
) {
}
