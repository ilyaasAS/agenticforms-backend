package com.agenticform.dto;

public record GoogleCalendarBookResponse(
        String eventId,
        String htmlLink,
        String meetLink,
        String startLabel,
        String startAt) {
}
