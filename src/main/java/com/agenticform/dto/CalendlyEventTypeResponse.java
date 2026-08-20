package com.agenticform.dto;

public record CalendlyEventTypeResponse(
        String uri,
        String name,
        String schedulingUrl,
        Integer durationMinutes,
        boolean active
) {
}
