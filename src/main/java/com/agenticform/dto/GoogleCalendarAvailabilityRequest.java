package com.agenticform.dto;

import jakarta.validation.constraints.NotBlank;

public record GoogleCalendarAvailabilityRequest(
        @NotBlank String timezone,
        int durationMinutes,
        int incrementMinutes,
        boolean minNoticeEnabled,
        int minNoticeHours,
        boolean bufferEnabled,
        String bufferBefore,
        String bufferAfter,
        boolean limitFutureBookings,
        String limitFutureMode,
        int limitFutureDays,
        String limitFutureStart,
        String limitFutureEnd,
        String availabilitySchedulesJson,
        String availabilityScheduleId,
        String calendarId,
        @NotBlank String fromDate,
        @NotBlank String toDate) {
}
