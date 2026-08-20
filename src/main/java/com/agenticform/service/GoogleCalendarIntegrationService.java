package com.agenticform.service;

import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;

import com.agenticform.dto.GuestInvite;
import com.agenticform.dto.GoogleCalendarAvailabilityRequest;
import com.agenticform.util.GuestInviteParser;
import com.agenticform.dto.GoogleCalendarAvailabilityResponse;
import com.agenticform.dto.GoogleCalendarBookRequest;
import com.agenticform.dto.GoogleCalendarBookResponse;
import com.agenticform.dto.PublicSchedulingEventResponse;
import com.agenticform.dto.GoogleCalendarDaySlotsResponse;
import com.agenticform.dto.GoogleCalendarItemResponse;
import com.agenticform.dto.GoogleCalendarListResponse;
import com.agenticform.dto.GoogleCalendarStatusResponse;
import com.agenticform.exception.GoogleCalendarIntegrationException;
import com.agenticform.exception.GoogleCalendarNotConfiguredException;
import com.agenticform.model.entity.Form;
import com.agenticform.model.entity.IntegrationConnection;
import com.agenticform.model.entity.User;
import com.agenticform.repository.IntegrationConnectionRepository;
import com.agenticform.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class GoogleCalendarIntegrationService {

    public static final String PROVIDER = "google_calendar";

    private static final String AUTHORIZE_URL = "https://accounts.google.com/o/oauth2/v2/auth";
    private static final String TOKEN_URL = "https://oauth2.googleapis.com/token";
    private static final String USERINFO_URL = "https://www.googleapis.com/oauth2/v2/userinfo";
    private static final String CALENDAR_API = "https://www.googleapis.com/calendar/v3";
    private static final String SCOPES = String.join(
            " ",
            "openid",
            "email",
            "https://www.googleapis.com/auth/calendar.events",
            "https://www.googleapis.com/auth/calendar.readonly");

    private static final Map<DayOfWeek, String> DAY_KEYS = Map.of(
            DayOfWeek.MONDAY, "mon",
            DayOfWeek.TUESDAY, "tue",
            DayOfWeek.WEDNESDAY, "wed",
            DayOfWeek.THURSDAY, "thu",
            DayOfWeek.FRIDAY, "fri",
            DayOfWeek.SATURDAY, "sat",
            DayOfWeek.SUNDAY, "sun");

    private final IntegrationConnectionRepository connectionRepository;
    private final UserRepository userRepository;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final BookingNotificationService bookingNotificationService;
    private final EmailService emailService;
    private final String clientId;
    private final String clientSecret;
    private final String redirectUri;
    private final String frontendRedirectUri;
    private final String stateSecret;

    public GoogleCalendarIntegrationService(
            IntegrationConnectionRepository connectionRepository,
            UserRepository userRepository,
            ObjectMapper objectMapper,
            BookingNotificationService bookingNotificationService,
            EmailService emailService,
            @Value("${app.google-calendar.client-id:}") String clientId,
            @Value("${app.google-calendar.client-secret:}") String clientSecret,
            @Value("${app.google-calendar.redirect-uri:http://localhost:5173/api/v1/integrations/google-calendar/callback}")
                    String redirectUri,
            @Value("${app.oauth2.frontend-redirect-uri:http://localhost:5173/oauth2/redirect}")
                    String frontendRedirectUri,
            @Value("${jwt.secret}") String jwtSecret) {
        this.connectionRepository = connectionRepository;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
        this.bookingNotificationService = bookingNotificationService;
        this.emailService = emailService;
        this.restClient = RestClient.create();
        this.clientId = clientId == null ? "" : clientId.trim();
        this.clientSecret = clientSecret == null ? "" : clientSecret.trim();
        this.redirectUri = redirectUri.trim();
        this.frontendRedirectUri = frontendRedirectUri.replace("/oauth2/redirect", "");
        this.stateSecret = jwtSecret;
    }

    public boolean isConfigured() {
        return StringUtils.hasText(clientId) && StringUtils.hasText(clientSecret);
    }

    public GoogleCalendarStatusResponse status(Long userId) {
        IntegrationConnection connection = connectionRepository
                .findByUserIdAndProvider(userId, PROVIDER)
                .orElse(null);
        return new GoogleCalendarStatusResponse(
                isConfigured(),
                connection != null,
                connection == null ? null : connection.getProviderEmail());
    }

    public String buildAuthorizeUrl(Long userId) {
        return buildAuthorizeUrl(userId, false);
    }

    public String buildAuthorizeUrl(Long userId, boolean switchAccount) {
        if (!isConfigured()) {
            throw new GoogleCalendarNotConfiguredException();
        }
        String state = signState(userId);
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(AUTHORIZE_URL)
                .queryParam("client_id", clientId)
                .queryParam("response_type", "code")
                .queryParam("redirect_uri", redirectUri)
                .queryParam("scope", SCOPES)
                .queryParam("state", state)
                .queryParam("access_type", "offline")
                .queryParam("include_granted_scopes", "true");
        if (switchAccount) {
            builder.queryParam("prompt", "consent select_account");
        } else {
            builder.queryParam("prompt", "consent");
        }
        return builder.encode().build().toUriString();
    }

    public String frontendCallbackUrl(boolean ok, String errorCode) {
        UriComponentsBuilder builder = UriComponentsBuilder
                .fromUriString(frontendRedirectUri + "/integrations/google-calendar/callback");
        if (ok) {
            builder.queryParam("ok", "1");
        } else {
            builder.queryParam("ok", "0");
            if (StringUtils.hasText(errorCode)) {
                builder.queryParam("error", errorCode);
            }
        }
        return builder.encode().build().toUriString();
    }

    @Transactional
    public void handleCallback(String code, String state) {
        if (!isConfigured()) {
            throw new GoogleCalendarNotConfiguredException();
        }
        if (!StringUtils.hasText(code) || !StringUtils.hasText(state)) {
            throw new GoogleCalendarIntegrationException("Autorisation Google Calendar incomplète.");
        }
        long userId = parseState(state);
        JsonNode token = exchangeToken("authorization_code", code, null);
        String accessToken = text(token, "access_token");
        String refreshToken = text(token, "refresh_token");
        Instant expiresAt = expiresAtFrom(token);
        if (!StringUtils.hasText(accessToken)) {
            throw new GoogleCalendarIntegrationException("Google n’a pas renvoyé de jeton d’accès.");
        }

        JsonNode userinfo = googleGet(USERINFO_URL, accessToken);
        String email = userinfo.path("email").asText(null);
        String primaryCalendarId = resolvePrimaryCalendarId(accessToken, email);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new GoogleCalendarIntegrationException("Utilisateur introuvable."));

        IntegrationConnection connection = connectionRepository
                .findByUserIdAndProvider(userId, PROVIDER)
                .orElseGet(IntegrationConnection::new);
        connection.setUser(user);
        connection.setProvider(PROVIDER);
        connection.setAccessToken(accessToken);
        if (StringUtils.hasText(refreshToken)) {
            connection.setRefreshToken(refreshToken);
        }
        connection.setExpiresAt(expiresAt);
        connection.setOwnerUri(primaryCalendarId);
        connection.setProviderEmail(email);
        connectionRepository.save(connection);
    }

    @Transactional
    public void disconnect(Long userId) {
        connectionRepository.deleteByUserIdAndProvider(userId, PROVIDER);
    }

    @Transactional(readOnly = true)
    public GoogleCalendarListResponse listCalendars(Long userId) {
        IntegrationConnection connection = optionalConnection(userId).orElse(null);
        if (connection == null) {
            return new GoogleCalendarListResponse(List.of());
        }
        String token = validAccessToken(connection);
        JsonNode root = googleGet(CALENDAR_API + "/users/me/calendarList", token);
        List<GoogleCalendarItemResponse> calendars = new ArrayList<>();
        JsonNode items = root.path("items");
        if (items.isArray()) {
            for (JsonNode item : items) {
                calendars.add(new GoogleCalendarItemResponse(
                        item.path("id").asText(null),
                        item.path("summary").asText("Calendrier"),
                        item.path("primary").asBoolean(false)));
            }
        }
        calendars.sort(Comparator.comparing(GoogleCalendarItemResponse::primary).reversed());
        return new GoogleCalendarListResponse(List.copyOf(calendars));
    }

    public GoogleCalendarAvailabilityResponse computeAvailability(
            Long userId,
            GoogleCalendarAvailabilityRequest request) {
        ZoneId zone = ZoneId.of(request.timezone());
        LocalDate from = LocalDate.parse(request.fromDate());
        LocalDate to = LocalDate.parse(request.toDate());
        if (to.isBefore(from)) {
            throw new GoogleCalendarIntegrationException("Plage de dates invalide.");
        }
        if (ChronoUnit.DAYS.between(from, to) > 62) {
            throw new GoogleCalendarIntegrationException("Plage de dates trop large (62 jours max).");
        }

        JsonNode schedule = resolveSchedule(request.availabilitySchedulesJson(), request.availabilityScheduleId());
        int increment = Math.max(5, request.incrementMinutes() > 0 ? request.incrementMinutes() : 30);
        int duration = Math.max(5, request.durationMinutes() > 0 ? request.durationMinutes() : 30);
        int bufferBefore = request.bufferEnabled() ? parseBufferMinutes(request.bufferBefore()) : 0;
        int bufferAfter = request.bufferEnabled() ? parseBufferMinutes(request.bufferAfter()) : 0;
        ZonedDateTime now = ZonedDateTime.now(zone);

        Optional<IntegrationConnection> connectionOpt = optionalConnection(userId);
        boolean connected = connectionOpt.isPresent();
        List<BusyInterval> busyIntervals = List.of();
        if (connected) {
            IntegrationConnection connection = connectionOpt.get();
            String token = validAccessToken(connection);
            String calendarId = StringUtils.hasText(request.calendarId())
                    ? request.calendarId()
                    : defaultCalendarId(connection);
            ZonedDateTime rangeStart = from.atStartOfDay(zone);
            ZonedDateTime rangeEnd = to.plusDays(1).atStartOfDay(zone);
            busyIntervals = fetchBusyIntervals(token, calendarId, rangeStart, rangeEnd, zone);
        }

        List<GoogleCalendarDaySlotsResponse> days = new ArrayList<>();
        LocalDate today = LocalDate.now(zone);
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            if (date.isBefore(today)) {
                days.add(new GoogleCalendarDaySlotsResponse(date.toString(), List.of()));
                continue;
            }
            if (!isDateAllowed(date, request, zone)) {
                days.add(new GoogleCalendarDaySlotsResponse(date.toString(), List.of()));
                continue;
            }
            List<LocalTime> candidates = generateCandidates(date, schedule, zone, increment, duration);
            List<String> slots = new ArrayList<>();
            for (LocalTime startTime : candidates) {
                ZonedDateTime start = ZonedDateTime.of(date, startTime, zone);
                ZonedDateTime end = start.plusMinutes(duration);
                if (start.isBefore(now)) {
                    continue;
                }
                if (request.minNoticeEnabled()) {
                    ZonedDateTime earliest = now.plusHours(Math.max(0, request.minNoticeHours()));
                    if (start.isBefore(earliest)) {
                        continue;
                    }
                }
                if (overlapsBusy(start, end, bufferBefore, bufferAfter, busyIntervals)) {
                    continue;
                }
                slots.add(startTime.format(DateTimeFormatter.ofPattern("HH:mm")));
            }
            days.add(new GoogleCalendarDaySlotsResponse(date.toString(), List.copyOf(slots)));
        }
        return new GoogleCalendarAvailabilityResponse(connected, days);
    }

    @Transactional
    public GoogleCalendarBookResponse createBooking(Long userId, GoogleCalendarBookRequest request) {
        return createBooking(userId, request, null, true);
    }

    public GoogleCalendarBookResponse createBooking(Long userId, GoogleCalendarBookRequest request, Long formId) {
        return createBooking(userId, request, formId, true);
    }

    @Transactional
    public GoogleCalendarBookResponse createBooking(Long userId, GoogleCalendarBookRequest request, Long formId, boolean sendNotification) {
        IntegrationConnection connection = requireConnection(userId);
        GoogleCalendarAvailabilityRequest availability = request.availability();
        GoogleCalendarAvailabilityResponse slots = computeAvailability(userId, availability);
        boolean stillOpen = slots.days().stream()
                .filter((day) -> request.date().equals(day.date()))
                .flatMap((day) -> day.slots().stream())
                .anyMatch((slot) -> request.startTime().equals(slot));
        if (!stillOpen) {
            throw new GoogleCalendarIntegrationException("Ce créneau n’est plus disponible.");
        }

        ZoneId zone = ZoneId.of(availability.timezone());
        LocalDate date = LocalDate.parse(request.date());
        LocalDate today = LocalDate.now(zone);
        if (date.isBefore(today)) {
            throw new GoogleCalendarIntegrationException("Ce créneau est passé. Choisissez une date future.");
        }
        LocalTime startTime = LocalTime.parse(request.startTime());
        int duration = Math.max(5, availability.durationMinutes() > 0 ? availability.durationMinutes() : 30);
        ZonedDateTime start = ZonedDateTime.of(date, startTime, zone);
        ZonedDateTime end = start.plusMinutes(duration);
        String calendarId = StringUtils.hasText(availability.calendarId())
                ? availability.calendarId()
                : defaultCalendarId(connection);
        boolean createMeet = "google_meet".equalsIgnoreCase(request.locationType());
        DateTimeFormatter googleDateTime = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

        Map<String, Object> event = new LinkedHashMap<>();
        event.put("summary", StringUtils.hasText(request.title()) ? request.title() : "Rendez-vous");
        if (StringUtils.hasText(request.description())) {
            event.put("description", request.description());
        }
        if (StringUtils.hasText(request.location()) && !createMeet) {
            event.put("location", request.location());
        }
        event.put("start", Map.of(
                "dateTime", start.format(googleDateTime),
                "timeZone", zone.getId()));
        event.put("end", Map.of(
                "dateTime", end.format(googleDateTime),
                "timeZone", zone.getId()));
        List<Map<String, Object>> attendees = new ArrayList<>();
        Map<String, Object> attendee = new LinkedHashMap<>();
        attendee.put("email", request.guestEmail().trim());
        if (StringUtils.hasText(request.guestName())) {
            attendee.put("displayName", request.guestName().trim());
        }
        attendees.add(attendee);
        List<GuestInvite> invitedGuests = GuestInviteParser.normalize(request.guestInvites(), request.guestEmail());
        for (GuestInvite invited : invitedGuests) {
            Map<String, Object> invitedAttendee = new LinkedHashMap<>();
            invitedAttendee.put("email", invited.email());
            if (StringUtils.hasText(invited.name())) {
                invitedAttendee.put("displayName", invited.name().trim());
            }
            attendees.add(invitedAttendee);
        }
        event.put("attendees", attendees);
        int reminderMinutes = request.reminderMinutes() != null ? Math.max(0, request.reminderMinutes()) : 0;
        if (reminderMinutes > 0) {
            event.put("reminders", Map.of(
                    "useDefault", false,
                    "overrides", List.of(
                            Map.of("method", "email", "minutes", reminderMinutes),
                            Map.of("method", "popup", "minutes", reminderMinutes))));
        }
        if (createMeet) {
            event.put("conferenceData", Map.of(
                    "createRequest", Map.of(
                            "requestId", UUID.randomUUID().toString(),
                            "conferenceSolutionKey", Map.of("type", "hangoutsMeet"))));
        }

        if (StringUtils.hasText(request.previousEventId())) {
            cancelBooking(userId, request.previousEventId(), calendarId);
        }

        boolean sendInvite = request.sendCalendarInvite() == null || request.sendCalendarInvite();
        String token = validAccessToken(connection);
        String encodedCalendarId = UriUtils.encodePathSegment(calendarId, StandardCharsets.UTF_8);
        String uri = CALENDAR_API + "/calendars/" + encodedCalendarId + "/events"
                + (sendInvite ? "?sendUpdates=all" : "?sendUpdates=none")
                + (createMeet ? "&conferenceDataVersion=1" : "");
        JsonNode created = googlePost(uri, token, event, "Impossible de créer l’événement Google Calendar.");
        String startLabel = start.format(DateTimeFormatter.ofPattern("d MMMM yyyy 'à' HH:mm", Locale.FRENCH));
        User owner = userRepository.findById(userId).orElse(null);
        String organizerEmail = owner != null ? owner.getEmail() : null;
        String organizerName = owner != null ? owner.getFullName() : null;
        String eventId = created.path("id").asText(null);
        if (sendNotification) {
            String cancelLink = null;
            if (formId != null && StringUtils.hasText(eventId)) {
                cancelLink = frontendRedirectUri + "/booking/" + formId + "/manage/" + eventId;
            }
            bookingNotificationService.notifyBooking(
                    request.guestEmail().trim(),
                    request.guestName(),
                    StringUtils.hasText(request.title()) ? request.title() : "Rendez-vous",
                    startLabel,
                    created.path("htmlLink").asText(null),
                    start.toInstant(),
                    reminderMinutes,
                    organizerEmail,
                    organizerName,
                    cancelLink,
                    invitedGuests,
                    request.guestName(),
                    request.guestEmail().trim());
        }
        return new GoogleCalendarBookResponse(
                eventId,
                created.path("htmlLink").asText(null),
                firstMeetLink(created),
                startLabel,
                start.toInstant().toString());
    }

    public void sendBookingNotification(Long formId, Long userId,
                                       String guestEmail, String guestName,
                                       String title, String startLabel,
                                       String htmlLink, String eventId,
                                       List<GuestInvite> invitedGuests) {
        User owner = userRepository.findById(userId).orElse(null);
        String organizerEmail = owner != null ? owner.getEmail() : null;
        String organizerName = owner != null ? owner.getFullName() : null;
        String cancelLink = null;
        if (formId != null && StringUtils.hasText(eventId)) {
            cancelLink = frontendRedirectUri + "/booking/" + formId + "/manage/" + eventId;
        }
        bookingNotificationService.notifyBooking(
                guestEmail, guestName, title, startLabel, htmlLink,
                null, 0, organizerEmail, organizerName, cancelLink,
                invitedGuests == null ? List.of() : invitedGuests, guestName, guestEmail);
    }

    public List<Map<String, String>> listUpcomingBookings(Long userId, String calendarId) {
        IntegrationConnection connection = requireConnection(userId);
        String token = validAccessToken(connection);
        String effectiveCalendarId = StringUtils.hasText(calendarId) ? calendarId : defaultCalendarId(connection);
        String encodedCalendarId = UriUtils.encodePathSegment(effectiveCalendarId, StandardCharsets.UTF_8);
        String timeMin = ZonedDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT);
        String uri = CALENDAR_API + "/calendars/" + encodedCalendarId
                + "/events?maxResults=50&singleEvents=true&orderBy=startTime&timeMin=" + timeMin;
        JsonNode root = googleGet(uri, token);
        List<Map<String, String>> result = new ArrayList<>();
        JsonNode items = root.path("items");
        if (items.isArray()) {
            for (JsonNode item : items) {
                Map<String, String> entry = new LinkedHashMap<>();
                entry.put("eventId", item.path("id").asText(null));
                entry.put("summary", item.path("summary").asText(""));
                entry.put("start", item.path("start").path("dateTime").asText(
                        item.path("start").path("date").asText("")));
                entry.put("end", item.path("end").path("dateTime").asText(
                        item.path("end").path("date").asText("")));
                entry.put("htmlLink", item.path("htmlLink").asText(null));
                JsonNode attendees = item.path("attendees");
                if (attendees.isArray() && !attendees.isEmpty()) {
                    entry.put("guestEmail", attendees.get(0).path("email").asText(""));
                    entry.put("guestName", attendees.get(0).path("displayName").asText(""));
                }
                result.add(entry);
            }
        }
        return result;
    }

    public com.fasterxml.jackson.databind.JsonNode getEvent(Long userId, String eventId, String calendarId) {
        IntegrationConnection connection = requireConnection(userId);
        String token = validAccessToken(connection);
        String effectiveCalendarId = StringUtils.hasText(calendarId) ? calendarId : defaultCalendarId(connection);
        String encodedCalendarId = UriUtils.encodePathSegment(effectiveCalendarId, StandardCharsets.UTF_8);
        String uri = CALENDAR_API + "/calendars/" + encodedCalendarId + "/events/" + eventId;
        try {
            return googleGet(uri, token);
        } catch (Exception ex) {
            return null;
        }
    }

    public PublicSchedulingEventResponse parsePublicSchedulingEvent(JsonNode event) {
        if (event == null || event.isNull()) {
            throw new GoogleCalendarIntegrationException("Réservation introuvable.");
        }

        JsonNode startNode = event.path("start");
        String dateTimeStr = startNode.path("dateTime").asText(null);
        String timezone = startNode.path("timeZone").asText("Europe/Paris");
        ZoneId zone = ZoneId.of(timezone);

        String date = null;
        String startTime = null;
        String startLabel = null;
        if (StringUtils.hasText(dateTimeStr)) {
            ZonedDateTime start = ZonedDateTime.parse(dateTimeStr).withZoneSameInstant(zone);
            date = start.toLocalDate().toString();
            startTime = start.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm"));
            startLabel = start.format(DateTimeFormatter.ofPattern("d MMMM yyyy 'à' HH:mm", Locale.FRENCH));
        }

        String guestEmail = null;
        String guestName = null;
        List<GuestInvite> guestInvites = new ArrayList<>();
        JsonNode attendees = event.path("attendees");
        if (attendees.isArray()) {
            List<JsonNode> guests = new ArrayList<>();
            for (JsonNode attendee : attendees) {
                if (attendee.path("organizer").asBoolean(false)) {
                    continue;
                }
                if (attendee.path("resource").asBoolean(false)) {
                    continue;
                }
                String email = attendee.path("email").asText(null);
                if (!StringUtils.hasText(email)) {
                    continue;
                }
                guests.add(attendee);
            }
            if (!guests.isEmpty()) {
                JsonNode primary = guests.get(0);
                guestEmail = primary.path("email").asText(null);
                guestName = primary.path("displayName").asText(null);
                for (int index = 1; index < guests.size(); index++) {
                    JsonNode invited = guests.get(index);
                    GuestInvite invite = GuestInviteParser.fromJsonItem(invited);
                    if (invite != null) {
                        guestInvites.add(invite);
                    }
                }
            }
        }

        return new PublicSchedulingEventResponse(
                guestEmail,
                guestName,
                List.copyOf(guestInvites),
                date,
                startTime,
                startLabel);
    }

    public void cancelBooking(Long userId, String eventId, String calendarId) {
        IntegrationConnection connection = requireConnection(userId);
        String token = validAccessToken(connection);
        String effectiveCalendarId = StringUtils.hasText(calendarId) ? calendarId : defaultCalendarId(connection);
        String encodedCalendarId = UriUtils.encodePathSegment(effectiveCalendarId, StandardCharsets.UTF_8);
        String uri = CALENDAR_API + "/calendars/" + encodedCalendarId + "/events/" + eventId + "?sendUpdates=all";
        try {
            restClient.delete()
                    .uri(uri)
                    .header("Authorization", "Bearer " + token)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception ignored) {
        }
    }

    public GoogleCalendarBookResponse rescheduleBooking(Long userId, String eventId, String calendarId,
                                                        String newDate, String newStartTime, int durationMinutes, String timezone) {
        IntegrationConnection connection = requireConnection(userId);
        String token = validAccessToken(connection);
        String effectiveCalendarId = StringUtils.hasText(calendarId) ? calendarId : defaultCalendarId(connection);
        String encodedCalendarId = UriUtils.encodePathSegment(effectiveCalendarId, StandardCharsets.UTF_8);

        var existingEvent = getEvent(userId, eventId, calendarId);
        String title = existingEvent != null ? existingEvent.path("summary").asText("Rendez-vous") : "Rendez-vous";
        String guestEmail = null;
        String guestName = null;
        if (existingEvent != null) {
            var attendees = existingEvent.path("attendees");
            if (attendees.isArray() && !attendees.isEmpty()) {
                guestEmail = attendees.get(0).path("email").asText(null);
                guestName = attendees.get(0).path("displayName").asText(null);
            }
        }

        ZoneId zone = ZoneId.of(timezone);
        LocalDate date = LocalDate.parse(newDate);
        LocalTime startTime = LocalTime.parse(newStartTime);
        int duration = Math.max(5, durationMinutes > 0 ? durationMinutes : 30);
        ZonedDateTime start = ZonedDateTime.of(date, startTime, zone);
        ZonedDateTime end = start.plusMinutes(duration);
        DateTimeFormatter googleDateTime = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

        Map<String, Object> patch = new LinkedHashMap<>();
        patch.put("start", Map.of("dateTime", start.format(googleDateTime), "timeZone", zone.getId()));
        patch.put("end", Map.of("dateTime", end.format(googleDateTime), "timeZone", zone.getId()));

        String uri = CALENDAR_API + "/calendars/" + encodedCalendarId + "/events/" + eventId + "?sendUpdates=all";
        try {
            String json = objectMapper.writeValueAsString(patch);
            restClient.method(org.springframework.http.HttpMethod.PATCH)
                    .uri(uri)
                    .header("Authorization", "Bearer " + token)
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .body(json)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception ex) {
            throw new GoogleCalendarIntegrationException("Impossible de modifier l'événement.");
        }

        String startLabel = start.format(DateTimeFormatter.ofPattern("d MMMM yyyy 'à' HH:mm", java.util.Locale.FRENCH));

        try {
            User owner = userRepository.findById(userId).orElse(null);
            String organizerEmail = owner != null ? owner.getEmail() : null;
            String organizerName = owner != null ? owner.getFullName() : null;
            if (guestEmail != null && !guestEmail.isBlank()) {
                bookingNotificationService.notifyBooking(
                        guestEmail, guestName, title, startLabel, null, null, 0,
                        organizerEmail, organizerName, null, List.of(), guestName, guestEmail);
            }
        } catch (Exception ignored) {
        }

        return new GoogleCalendarBookResponse(eventId, null, null, startLabel, start.toInstant().toString());
    }

    public void cancelBookingWithNotification(Long userId, String eventId, String calendarId) {
        var event = getEvent(userId, eventId, calendarId);
        String title = event != null ? event.path("summary").asText("Rendez-vous") : "Rendez-vous";
        String guestEmail = null;
        String guestName = null;
        if (event != null) {
            var attendees = event.path("attendees");
            if (attendees.isArray() && !attendees.isEmpty()) {
                guestEmail = attendees.get(0).path("email").asText(null);
                guestName = attendees.get(0).path("displayName").asText(null);
            }
        }
        cancelBooking(userId, eventId, calendarId);
        try {
            User owner = userRepository.findById(userId).orElse(null);
            if (guestEmail != null && !guestEmail.isBlank()) {
                emailService.sendBookingCancellationEmail(guestEmail, guestName, title);
            }
            if (owner != null && owner.getEmail() != null) {
                emailService.sendCancellationNotificationToOrganizer(
                        owner.getEmail(), owner.getFullName(), guestName, guestEmail, title);
            }
        } catch (Exception ignored) {
        }
    }

    public Long ownerUserIdForPublishedForm(Form form) {
        if (form.getWorkspace() != null && form.getWorkspace().getOwner() != null) {
            return form.getWorkspace().getOwner().getId();
        }
        if (form.getCreatedBy() != null) {
            return form.getCreatedBy().getId();
        }
        throw new GoogleCalendarIntegrationException("Organisateur du formulaire introuvable.");
    }

    private String firstMeetLink(JsonNode created) {
        String hangout = created.path("hangoutLink").asText(null);
        if (StringUtils.hasText(hangout)) {
            return hangout;
        }
        JsonNode entryPoints = created.path("conferenceData").path("entryPoints");
        if (entryPoints.isArray()) {
            for (JsonNode point : entryPoints) {
                if ("video".equals(point.path("entryPointType").asText())) {
                    return point.path("uri").asText(null);
                }
            }
        }
        return null;
    }

    private Optional<IntegrationConnection> optionalConnection(Long userId) {
        return connectionRepository.findByUserIdAndProvider(userId, PROVIDER);
    }

    private IntegrationConnection requireConnection(Long userId) {
        return optionalConnection(userId)
                .orElseThrow(() -> new GoogleCalendarIntegrationException("Google Calendar non connecté."));
    }

    private String defaultCalendarId(IntegrationConnection connection) {
        if (StringUtils.hasText(connection.getOwnerUri())) {
            return connection.getOwnerUri();
        }
        return "primary";
    }

    private String resolvePrimaryCalendarId(String accessToken, String email) {
        try {
            JsonNode root = googleGet(CALENDAR_API + "/users/me/calendarList", accessToken);
            JsonNode items = root.path("items");
            if (items.isArray()) {
                for (JsonNode item : items) {
                    if (item.path("primary").asBoolean(false)) {
                        String id = item.path("id").asText(null);
                        if (StringUtils.hasText(id)) {
                            return id;
                        }
                    }
                }
            }
        } catch (RuntimeException ex) {
            // fallback below
        }
        return StringUtils.hasText(email) ? email : "primary";
    }

    private JsonNode resolveSchedule(String schedulesJson, String scheduleId) {
        try {
            if (StringUtils.hasText(schedulesJson)) {
                JsonNode parsed = objectMapper.readTree(schedulesJson);
                if (parsed.isArray() && parsed.size() > 0) {
                    JsonNode selected = null;
                    for (JsonNode item : parsed) {
                        if (scheduleId != null && scheduleId.equals(item.path("id").asText(null))) {
                            selected = item;
                            break;
                        }
                    }
                    if (selected == null) {
                        for (JsonNode item : parsed) {
                            if (item.path("isDefault").asBoolean(false)) {
                                selected = item;
                                break;
                            }
                        }
                    }
                    if (selected == null) {
                        selected = parsed.get(0);
                    }
                    return selected;
                }
            }
        } catch (Exception ex) {
            // default below
        }
        return defaultWorkingHoursSchedule();
    }

    private JsonNode defaultWorkingHoursSchedule() {
        try {
            return objectMapper.readTree("""
                    {
                      "id":"working_hours",
                      "name":"Heures de bureau",
                      "timezone":"Europe/Paris",
                      "isDefault":true,
                      "days":{
                        "mon":{"enabled":true,"slots":[{"start":"09:00","end":"17:00"}]},
                        "tue":{"enabled":true,"slots":[{"start":"09:00","end":"17:00"}]},
                        "wed":{"enabled":true,"slots":[{"start":"09:00","end":"17:00"}]},
                        "thu":{"enabled":true,"slots":[{"start":"09:00","end":"17:00"}]},
                        "fri":{"enabled":true,"slots":[{"start":"09:00","end":"17:00"}]},
                        "sat":{"enabled":false,"slots":[{"start":"09:00","end":"17:00"}]},
                        "sun":{"enabled":false,"slots":[{"start":"09:00","end":"17:00"}]}
                      }
                    }
                    """);
        } catch (Exception ex) {
            throw new GoogleCalendarIntegrationException("Impossible de charger les heures par défaut.");
        }
    }

    private boolean isDateAllowed(LocalDate date, GoogleCalendarAvailabilityRequest request, ZoneId zone) {
        if (!request.limitFutureBookings()) {
            return true;
        }
        LocalDate today = LocalDate.now(zone);
        if (date.isBefore(today)) {
            return false;
        }
        if ("range".equalsIgnoreCase(request.limitFutureMode())) {
            LocalDate start = parseDateOrNull(request.limitFutureStart());
            LocalDate end = parseDateOrNull(request.limitFutureEnd());
            if (start != null && date.isBefore(start)) {
                return false;
            }
            if (end != null && date.isAfter(end)) {
                return false;
            }
            return true;
        }
        int days = Math.max(1, request.limitFutureDays());
        return !date.isAfter(today.plusDays(days));
    }

    private LocalDate parseDateOrNull(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            return LocalDate.parse(raw.trim());
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private List<LocalTime> generateCandidates(
            LocalDate date,
            JsonNode schedule,
            ZoneId zone,
            int incrementMinutes,
            int durationMinutes) {
        String dayKey = DAY_KEYS.get(date.getDayOfWeek());
        JsonNode day = schedule.path("days").path(dayKey);
        if (!day.path("enabled").asBoolean(false)) {
            return List.of();
        }
        JsonNode slots = day.path("slots");
        if (!slots.isArray() || slots.isEmpty()) {
            return List.of();
        }
        List<LocalTime> candidates = new ArrayList<>();
        for (JsonNode slot : slots) {
            LocalTime start = LocalTime.parse(slot.path("start").asText("09:00"));
            LocalTime end = LocalTime.parse(slot.path("end").asText("17:00"));
            LocalTime cursor = start;
            while (!cursor.plusMinutes(durationMinutes).isAfter(end)) {
                candidates.add(cursor);
                cursor = cursor.plusMinutes(incrementMinutes);
            }
        }
        candidates.sort(LocalTime::compareTo);
        return candidates;
    }

    private record BusyInterval(Instant start, Instant end) {
    }

    private List<BusyInterval> fetchBusyIntervals(
            String accessToken,
            String calendarId,
            ZonedDateTime rangeStart,
            ZonedDateTime rangeEnd,
            ZoneId zone) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("timeMin", rangeStart.toInstant().toString());
        payload.put("timeMax", rangeEnd.toInstant().toString());
        payload.put("timeZone", zone.getId());
        payload.put("items", List.of(Map.of("id", calendarId)));
        try {
            JsonNode body = restClient.post()
                    .uri(CALENDAR_API + "/freeBusy")
                    .header("Authorization", "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .body(JsonNode.class);
            if (body == null) {
                return List.of();
            }
            List<BusyInterval> busy = new ArrayList<>();
            JsonNode calendarBusy = body.path("calendars").path(calendarId).path("busy");
            if (calendarBusy.isArray()) {
                for (JsonNode item : calendarBusy) {
                    Instant start = Instant.parse(item.path("start").asText());
                    Instant end = Instant.parse(item.path("end").asText());
                    busy.add(new BusyInterval(start, end));
                }
            }
            return busy;
        } catch (RestClientException ex) {
            throw new GoogleCalendarIntegrationException("Impossible de lire les disponibilités Google Calendar.", ex);
        }
    }

    private boolean overlapsBusy(
            ZonedDateTime start,
            ZonedDateTime end,
            int bufferBeforeMinutes,
            int bufferAfterMinutes,
            List<BusyInterval> busyIntervals) {
        Instant slotStart = start.minusMinutes(bufferBeforeMinutes).toInstant();
        Instant slotEnd = end.plusMinutes(bufferAfterMinutes).toInstant();
        for (BusyInterval busy : busyIntervals) {
            if (slotStart.isBefore(busy.end()) && slotEnd.isAfter(busy.start())) {
                return true;
            }
        }
        return false;
    }

    private int parseBufferMinutes(String raw) {
        if (!StringUtils.hasText(raw) || "none".equalsIgnoreCase(raw.trim())) {
            return 0;
        }
        String value = raw.trim().toLowerCase(Locale.ROOT);
        if (value.endsWith("min")) {
            String digits = value.replace("min", "").trim();
            return parseIntOrZero(digits);
        }
        if (value.contains("hour")) {
            String digits = value.replace("hours", "").replace("hour", "").trim();
            double hours = parseDoubleOrZero(digits);
            return (int) Math.round(hours * 60);
        }
        return parseIntOrZero(value);
    }

    private int parseIntOrZero(String raw) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private double parseDoubleOrZero(String raw) {
        try {
            return Double.parseDouble(raw.trim());
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private String validAccessToken(IntegrationConnection connection) {
        Instant expiresAt = connection.getExpiresAt();
        if (expiresAt == null || Instant.now().isBefore(expiresAt.minus(java.time.Duration.ofMinutes(1)))) {
            return connection.getAccessToken();
        }
        if (!StringUtils.hasText(connection.getRefreshToken())) {
            throw new GoogleCalendarIntegrationException("Session Google Calendar expirée. Reconnectez-vous.");
        }
        JsonNode token = exchangeToken("refresh_token", null, connection.getRefreshToken());
        String accessToken = text(token, "access_token");
        if (!StringUtils.hasText(accessToken)) {
            throw new GoogleCalendarIntegrationException("Impossible de renouveler la session Google Calendar.");
        }
        connection.setAccessToken(accessToken);
        if (StringUtils.hasText(text(token, "refresh_token"))) {
            connection.setRefreshToken(text(token, "refresh_token"));
        }
        connection.setExpiresAt(expiresAtFrom(token));
        connectionRepository.save(connection);
        return accessToken;
    }

    private JsonNode exchangeToken(String grantType, String code, String refreshToken) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", grantType);
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        if ("authorization_code".equals(grantType)) {
            form.add("code", code);
            form.add("redirect_uri", redirectUri);
        } else {
            form.add("refresh_token", refreshToken);
        }
        try {
            JsonNode body = restClient.post()
                    .uri(TOKEN_URL)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(JsonNode.class);
            if (body == null) {
                throw new GoogleCalendarIntegrationException("Réponse Google vide.");
            }
            return body;
        } catch (RestClientException ex) {
            throw new GoogleCalendarIntegrationException("Échec de la connexion à Google Calendar.", ex);
        }
    }

    private JsonNode googleGet(String url, String accessToken) {
        try {
            JsonNode body = restClient.get()
                    .uri(url)
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .body(JsonNode.class);
            if (body == null) {
                throw new GoogleCalendarIntegrationException("Réponse Google vide.");
            }
            return body;
        } catch (RestClientException ex) {
            throw new GoogleCalendarIntegrationException("Impossible de lire Google Calendar.", ex);
        }
    }

    private JsonNode googlePost(String url, String accessToken, Object payload, String fallbackMessage) {
        try {
            JsonNode body = restClient.post()
                    .uri(url)
                    .header("Authorization", "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .body(JsonNode.class);
            if (body == null) {
                throw new GoogleCalendarIntegrationException("Réponse Google vide.");
            }
            return body;
        } catch (RestClientResponseException ex) {
            throw new GoogleCalendarIntegrationException(
                    fallbackMessage + " " + summarizeGoogleError(ex),
                    ex);
        } catch (RestClientException ex) {
            throw new GoogleCalendarIntegrationException(fallbackMessage, ex);
        }
    }

    private String summarizeGoogleError(RestClientResponseException ex) {
        try {
            JsonNode body = objectMapper.readTree(ex.getResponseBodyAsString());
            JsonNode error = body.path("error");
            String message = error.path("message").asText(null);
            if (!StringUtils.hasText(message) && error.path("errors").isArray() && error.path("errors").size() > 0) {
                message = error.path("errors").get(0).path("message").asText(null);
            }
            if (StringUtils.hasText(message)) {
                if (ex.getStatusCode().value() == 403) {
                    return "Reconnectez Google Calendar dans Paramètres → Calendriers (autorisations insuffisantes). Détail : "
                            + message;
                }
                return "Détail Google : " + message;
            }
        } catch (Exception ignored) {
            // ignore parse errors
        }
        return "Code HTTP " + ex.getStatusCode().value() + ".";
    }

    private Instant expiresAtFrom(JsonNode token) {
        int seconds = token.path("expires_in").asInt(3600);
        return Instant.now().plusSeconds(Math.max(60, seconds));
    }

    private static String text(JsonNode node, String field) {
        String value = node.path(field).asText(null);
        return StringUtils.hasText(value) ? value : null;
    }

    private String signState(Long userId) {
        String nonce = UUID.randomUUID().toString().replace("-", "");
        String payload = userId + "." + Instant.now().getEpochSecond() + "." + nonce;
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString((payload + "." + hmac(payload)).getBytes(StandardCharsets.UTF_8));
    }

    private long parseState(String state) {
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(state), StandardCharsets.UTF_8);
            String[] parts = decoded.split("\\.");
            if (parts.length != 4) {
                throw new GoogleCalendarIntegrationException("État OAuth Google Calendar invalide.");
            }
            String payload = parts[0] + "." + parts[1] + "." + parts[2];
            if (!hmac(payload).equals(parts[3])) {
                throw new GoogleCalendarIntegrationException("État OAuth Google Calendar invalide.");
            }
            long issued = Long.parseLong(parts[1]);
            if (Instant.now().getEpochSecond() - issued > 600) {
                throw new GoogleCalendarIntegrationException("La connexion Google Calendar a expiré. Réessayez.");
            }
            return Long.parseLong(parts[0]);
        } catch (GoogleCalendarIntegrationException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new GoogleCalendarIntegrationException("État OAuth Google Calendar invalide.");
        }
    }

    private String hmac(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(stateSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("Impossible de signer l’état OAuth Google Calendar.", ex);
        }
    }
}
