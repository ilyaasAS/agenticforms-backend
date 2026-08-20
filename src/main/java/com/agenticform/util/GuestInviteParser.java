package com.agenticform.util;

import java.util.ArrayList;
import java.util.List;

import org.springframework.util.StringUtils;

import com.agenticform.dto.GuestInvite;
import com.fasterxml.jackson.databind.JsonNode;

public final class GuestInviteParser {

    private GuestInviteParser() {
    }

    public static List<GuestInvite> fromJsonNode(JsonNode invitesNode) {
        if (invitesNode == null || !invitesNode.isArray()) {
            return List.of();
        }
        List<GuestInvite> invitedGuests = new ArrayList<>();
        for (JsonNode item : invitesNode) {
            GuestInvite invite = fromJsonItem(item);
            if (invite != null) {
                invitedGuests.add(invite);
            }
        }
        return invitedGuests;
    }

    public static GuestInvite fromJsonItem(JsonNode item) {
        if (item == null || item.isNull()) {
            return null;
        }
        if (item.isTextual()) {
            String email = item.asText(null);
            return emailFromString(email, null);
        }
        if (item.isObject()) {
            String email = item.path("email").asText(null);
            String name = item.path("name").asText(null);
            if (!StringUtils.hasText(name)) {
                name = item.path("displayName").asText(null);
            }
            return emailFromString(email, name);
        }
        return null;
    }

    public static List<GuestInvite> normalize(List<GuestInvite> guestInvites, String primaryGuestEmail) {
        if (guestInvites == null || guestInvites.isEmpty()) {
            return List.of();
        }
        String primary = primaryGuestEmail == null ? "" : primaryGuestEmail.trim().toLowerCase();
        List<GuestInvite> normalized = new ArrayList<>();
        for (GuestInvite invite : guestInvites) {
            if (invite == null || !StringUtils.hasText(invite.email())) {
                continue;
            }
            String email = invite.email().trim().toLowerCase();
            if (!email.contains("@") || email.equals(primary)) {
                continue;
            }
            boolean duplicate = normalized.stream().anyMatch((existing) -> existing.email().equals(email));
            if (duplicate) {
                continue;
            }
            String name = StringUtils.hasText(invite.name()) ? invite.name().trim() : null;
            normalized.add(new GuestInvite(email, name));
        }
        return List.copyOf(normalized);
    }

    private static GuestInvite emailFromString(String email, String name) {
        if (!StringUtils.hasText(email) || !email.trim().contains("@")) {
            return null;
        }
        String trimmedName = StringUtils.hasText(name) ? name.trim() : null;
        return new GuestInvite(email.trim().toLowerCase(), trimmedName);
    }
}
