package com.agenticform.dto;

import java.util.List;

public record GoogleCalendarListResponse(List<GoogleCalendarItemResponse> calendars) {
}
