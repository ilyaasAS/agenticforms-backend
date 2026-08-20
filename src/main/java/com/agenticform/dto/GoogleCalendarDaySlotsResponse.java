package com.agenticform.dto;

import java.util.List;

public record GoogleCalendarDaySlotsResponse(String date, List<String> slots) {
}
