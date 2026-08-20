package com.agenticform.service;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
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
import org.springframework.web.util.UriComponentsBuilder;

import com.agenticform.dto.CalendlyEventTypeResponse;
import com.agenticform.dto.CalendlyEventTypesResponse;
import com.agenticform.dto.CalendlyStatusResponse;
import com.agenticform.exception.CalendlyIntegrationException;
import com.agenticform.exception.CalendlyNotConfiguredException;
import com.agenticform.model.entity.IntegrationConnection;
import com.agenticform.model.entity.User;
import com.agenticform.repository.IntegrationConnectionRepository;
import com.agenticform.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;

@Service
public class CalendlyIntegrationService {

    public static final String PROVIDER = "calendly";

    private static final String AUTHORIZE_URL = "https://auth.calendly.com/oauth/authorize";
    private static final String TOKEN_URL = "https://auth.calendly.com/oauth/token";
    private static final String API_BASE = "https://api.calendly.com";

    private final IntegrationConnectionRepository connectionRepository;
    private final UserRepository userRepository;
    private final RestClient restClient;
    private final String clientId;
    private final String clientSecret;
    private final String redirectUri;
    private final String frontendRedirectUri;
    private final String stateSecret;

    public CalendlyIntegrationService(
            IntegrationConnectionRepository connectionRepository,
            UserRepository userRepository,
            @Value("${app.calendly.client-id:}") String clientId,
            @Value("${app.calendly.client-secret:}") String clientSecret,
            @Value("${app.calendly.redirect-uri:http://localhost:5173/api/v1/integrations/calendly/callback}")
                    String redirectUri,
            @Value("${app.oauth2.frontend-redirect-uri:http://localhost:5173/oauth2/redirect}")
                    String frontendRedirectUri,
            @Value("${jwt.secret}") String jwtSecret) {
        this.connectionRepository = connectionRepository;
        this.userRepository = userRepository;
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

    public CalendlyStatusResponse status(Long userId) {
        IntegrationConnection connection = connectionRepository
                .findByUserIdAndProvider(userId, PROVIDER)
                .orElse(null);
        return new CalendlyStatusResponse(
                isConfigured(),
                connection != null,
                connection == null ? null : connection.getProviderEmail());
    }

    public String buildAuthorizeUrl(Long userId) {
        return buildAuthorizeUrl(userId, false);
    }

    public String buildAuthorizeUrl(Long userId, boolean switchAccount) {
        if (!isConfigured()) {
            throw new CalendlyNotConfiguredException();
        }
        String state = signState(userId);
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(AUTHORIZE_URL)
                .queryParam("client_id", clientId)
                .queryParam("response_type", "code")
                .queryParam("redirect_uri", redirectUri)
                .queryParam("state", state);
        if (switchAccount) {
            builder.queryParam("prompt", "login");
        }
        return builder.encode().build().toUriString();
    }

    public String frontendCallbackUrl(boolean ok, String errorCode) {
        UriComponentsBuilder builder = UriComponentsBuilder
                .fromUriString(frontendRedirectUri + "/integrations/calendly/callback");
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
            throw new CalendlyNotConfiguredException();
        }
        if (!StringUtils.hasText(code) || !StringUtils.hasText(state)) {
            throw new CalendlyIntegrationException("Autorisation Calendly incomplète.");
        }
        long userId = parseState(state);
        JsonNode token = exchangeToken("authorization_code", code, null);
        String accessToken = text(token, "access_token");
        String refreshToken = text(token, "refresh_token");
        Instant expiresAt = expiresAtFrom(token);
        if (!StringUtils.hasText(accessToken)) {
            throw new CalendlyIntegrationException("Calendly n’a pas renvoyé de jeton d’accès.");
        }

        JsonNode me = calendlyGet("/users/me", accessToken);
        JsonNode resource = me.path("resource");
        String ownerUri = resource.path("uri").asText(null);
        String email = resource.path("email").asText(null);
        String organization = text(token, "organization");

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CalendlyIntegrationException("Utilisateur introuvable."));

        IntegrationConnection connection = connectionRepository
                .findByUserIdAndProvider(userId, PROVIDER)
                .orElseGet(IntegrationConnection::new);
        connection.setUser(user);
        connection.setProvider(PROVIDER);
        connection.setAccessToken(accessToken);
        connection.setRefreshToken(refreshToken);
        connection.setExpiresAt(expiresAt);
        connection.setOwnerUri(ownerUri);
        connection.setOrganizationUri(organization);
        connection.setProviderEmail(email);
        connectionRepository.save(connection);
    }

    @Transactional
    public void disconnect(Long userId) {
        connectionRepository.deleteByUserIdAndProvider(userId, PROVIDER);
    }

    @Transactional
    public CalendlyEventTypesResponse listEventTypes(Long userId) {
        IntegrationConnection connection = requireConnection(userId);
        String token = validAccessToken(connection);
        String ownerUri = connection.getOwnerUri();
        if (!StringUtils.hasText(ownerUri)) {
            JsonNode me = calendlyGet("/users/me", token);
            ownerUri = me.path("resource").path("uri").asText(null);
            connection.setOwnerUri(ownerUri);
            connectionRepository.save(connection);
        }
        if (!StringUtils.hasText(ownerUri)) {
            throw new CalendlyIntegrationException("Impossible de lire le compte Calendly.");
        }
        String eventTypesPath = UriComponentsBuilder.fromPath("/event_types")
                .queryParam("user", ownerUri)
                .queryParam("active", true)
                .encode()
                .build()
                .toUriString();
        JsonNode root = calendlyGet(eventTypesPath, token);
        List<CalendlyEventTypeResponse> events = new ArrayList<>();
        JsonNode collection = root.path("collection");
        if (collection.isArray()) {
            for (JsonNode item : collection) {
                events.add(new CalendlyEventTypeResponse(
                        item.path("uri").asText(null),
                        item.path("name").asText("Événement"),
                        item.path("scheduling_url").asText(null),
                        item.path("duration").isNumber() ? item.path("duration").asInt() : null,
                        item.path("active").asBoolean(true)));
            }
        }
        return new CalendlyEventTypesResponse(List.copyOf(events));
    }

    private IntegrationConnection requireConnection(Long userId) {
        return connectionRepository.findByUserIdAndProvider(userId, PROVIDER)
                .orElseThrow(() -> new CalendlyIntegrationException("Compte Calendly non connecté."));
    }

    private String validAccessToken(IntegrationConnection connection) {
        Instant expiresAt = connection.getExpiresAt();
        if (expiresAt == null || Instant.now().isBefore(expiresAt.minus(Duration.ofMinutes(1)))) {
            return connection.getAccessToken();
        }
        if (!StringUtils.hasText(connection.getRefreshToken())) {
            throw new CalendlyIntegrationException("Session Calendly expirée. Reconnectez-vous.");
        }
        JsonNode token = exchangeToken("refresh_token", null, connection.getRefreshToken());
        String accessToken = text(token, "access_token");
        if (!StringUtils.hasText(accessToken)) {
            throw new CalendlyIntegrationException("Impossible de renouveler la session Calendly.");
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
                throw new CalendlyIntegrationException("Réponse Calendly vide.");
            }
            return body;
        } catch (RestClientException ex) {
            throw new CalendlyIntegrationException("Échec de la connexion à Calendly.", ex);
        }
    }

    private JsonNode calendlyGet(String path, String accessToken) {
        try {
            JsonNode body = restClient.get()
                    .uri(API_BASE + path)
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .body(JsonNode.class);
            if (body == null) {
                throw new CalendlyIntegrationException("Réponse Calendly vide.");
            }
            return body;
        } catch (RestClientException ex) {
            throw new CalendlyIntegrationException("Impossible de lire les événements Calendly.", ex);
        }
    }

    private Instant expiresAtFrom(JsonNode token) {
        int seconds = token.path("expires_in").asInt(7200);
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
                throw new CalendlyIntegrationException("État OAuth Calendly invalide.");
            }
            String payload = parts[0] + "." + parts[1] + "." + parts[2];
            if (!hmac(payload).equals(parts[3])) {
                throw new CalendlyIntegrationException("État OAuth Calendly invalide.");
            }
            long issued = Long.parseLong(parts[1]);
            if (Instant.now().getEpochSecond() - issued > 600) {
                throw new CalendlyIntegrationException("La connexion Calendly a expiré. Réessayez.");
            }
            return Long.parseLong(parts[0]);
        } catch (CalendlyIntegrationException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new CalendlyIntegrationException("État OAuth Calendly invalide.");
        }
    }

    private String hmac(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(stateSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("Impossible de signer l’état OAuth Calendly.", ex);
        }
    }
}
