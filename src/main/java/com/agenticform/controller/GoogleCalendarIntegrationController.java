package com.agenticform.controller;

import java.net.URI;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.agenticform.dto.GoogleCalendarAvailabilityRequest;
import com.agenticform.dto.GoogleCalendarAvailabilityResponse;
import com.agenticform.dto.GoogleCalendarBookRequest;
import com.agenticform.dto.GoogleCalendarBookResponse;
import com.agenticform.dto.GoogleCalendarListResponse;
import com.agenticform.dto.GoogleCalendarStatusResponse;
import com.agenticform.exception.GoogleCalendarIntegrationException;
import com.agenticform.exception.GoogleCalendarNotConfiguredException;
import com.agenticform.security.UserPrincipal;
import com.agenticform.service.GoogleCalendarIntegrationService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/integrations/google-calendar")
public class GoogleCalendarIntegrationController {

    private final GoogleCalendarIntegrationService googleCalendarIntegrationService;

    public GoogleCalendarIntegrationController(GoogleCalendarIntegrationService googleCalendarIntegrationService) {
        this.googleCalendarIntegrationService = googleCalendarIntegrationService;
    }

    @GetMapping("/authorize")
    public ResponseEntity<Void> authorize(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(name = "switch", defaultValue = "false") boolean switchAccount) {
        Long userId = requireUserId(principal);
        if (!googleCalendarIntegrationService.isConfigured()) {
            return redirect(googleCalendarIntegrationService.frontendCallbackUrl(false, "not_configured"));
        }
        return redirect(googleCalendarIntegrationService.buildAuthorizeUrl(userId, switchAccount));
    }

    @GetMapping("/callback")
    public ResponseEntity<Void> callback(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String error) {
        if (error != null && !error.isBlank()) {
            return redirect(googleCalendarIntegrationService.frontendCallbackUrl(false, error));
        }
        try {
            googleCalendarIntegrationService.handleCallback(code, state);
            return redirect(googleCalendarIntegrationService.frontendCallbackUrl(true, null));
        } catch (GoogleCalendarNotConfiguredException ex) {
            return redirect(googleCalendarIntegrationService.frontendCallbackUrl(false, "not_configured"));
        } catch (GoogleCalendarIntegrationException ex) {
            return redirect(googleCalendarIntegrationService.frontendCallbackUrl(false, "oauth_failed"));
        } catch (RuntimeException ex) {
            return redirect(googleCalendarIntegrationService.frontendCallbackUrl(false, "oauth_failed"));
        }
    }

    @GetMapping
    public ResponseEntity<GoogleCalendarStatusResponse> status(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(googleCalendarIntegrationService.status(requireUserId(principal)));
    }

    @GetMapping("/calendars")
    public ResponseEntity<GoogleCalendarListResponse> calendars(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(googleCalendarIntegrationService.listCalendars(requireUserId(principal)));
    }

    @PostMapping("/availability")
    public ResponseEntity<GoogleCalendarAvailabilityResponse> availability(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody GoogleCalendarAvailabilityRequest request) {
        return ResponseEntity.ok(
                googleCalendarIntegrationService.computeAvailability(requireUserId(principal), request));
    }

    @PostMapping("/events")
    public ResponseEntity<GoogleCalendarBookResponse> book(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody GoogleCalendarBookRequest request,
            @RequestParam(required = false) Long formId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                googleCalendarIntegrationService.createBooking(requireUserId(principal), request, formId));
    }

    @GetMapping("/bookings")
    public ResponseEntity<java.util.List<java.util.Map<String, String>>> listBookings(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) String calendarId) {
        return ResponseEntity.ok(
                googleCalendarIntegrationService.listUpcomingBookings(requireUserId(principal), calendarId));
    }

    @PostMapping("/events/{eventId}/reschedule")
    public ResponseEntity<GoogleCalendarBookResponse> rescheduleEvent(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String eventId,
            @RequestParam String date,
            @RequestParam String startTime,
            @RequestParam(defaultValue = "30") int durationMinutes,
            @RequestParam(defaultValue = "Europe/Paris") String timezone,
            @RequestParam(required = false) String calendarId) {
        return ResponseEntity.ok(googleCalendarIntegrationService.rescheduleBooking(
                requireUserId(principal), eventId, calendarId, date, startTime, durationMinutes, timezone));
    }

    @DeleteMapping("/events/{eventId}")
    public ResponseEntity<Void> cancelEvent(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String eventId,
            @RequestParam(required = false) String calendarId,
            @RequestParam(defaultValue = "false") boolean notify) {
        Long userId = requireUserId(principal);
        if (notify) {
            googleCalendarIntegrationService.cancelBookingWithNotification(userId, eventId, calendarId);
        } else {
            googleCalendarIntegrationService.cancelBooking(userId, eventId, calendarId);
        }
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping
    public ResponseEntity<Void> disconnect(@AuthenticationPrincipal UserPrincipal principal) {
        googleCalendarIntegrationService.disconnect(requireUserId(principal));
        return ResponseEntity.noContent().build();
    }

    private Long requireUserId(UserPrincipal principal) {
        if (principal == null) {
            throw new AuthenticationCredentialsNotFoundException("Authentification requise.");
        }
        return principal.getId();
    }

    private static ResponseEntity<Void> redirect(String url) {
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(url)).build();
    }
}
