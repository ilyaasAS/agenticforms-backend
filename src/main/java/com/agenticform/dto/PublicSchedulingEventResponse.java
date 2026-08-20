package com.agenticform.dto;

import java.util.List;

public record PublicSchedulingEventResponse(
        String guestEmail,
        String guestName,
        List<GuestInvite> guestInvites,
        String date,
        String startTime,
        String startLabel) {
}
