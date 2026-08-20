package com.agenticform.dto;

import java.util.List;

public record GoogleCalendarAvailabilityResponse(
        boolean calendarConnected,
        List<GoogleCalendarDaySlotsResponse> days) {
}
