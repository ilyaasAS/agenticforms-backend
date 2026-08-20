package com.agenticform.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record GoogleCalendarBookRequest(
        @NotBlank String date,
        @NotBlank String startTime,
        @NotBlank @Email String guestEmail,
        String guestName,
        @Valid List<GuestInvite> guestInvites,
        String title,
        String description,
        String location,
        String locationType,
        Boolean sendCalendarInvite,
        Integer reminderMinutes,
        String previousEventId,
        @NotNull @Valid GoogleCalendarAvailabilityRequest availability) {
}
