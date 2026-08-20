package com.agenticform.service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

import com.agenticform.dto.FormLoginVerifyResponse;
import com.agenticform.exception.FormNotAvailableException;
import com.agenticform.exception.FormNotFoundException;
import com.agenticform.model.entity.Form;
import com.agenticform.model.entity.FormStatus;
import com.agenticform.repository.FormRepository;
import com.fasterxml.jackson.databind.JsonNode;

@Service
public class FormGoogleLoginService {

    private static final String AUTHORIZE_URL = "https://accounts.google.com/o/oauth2/v2/auth";
    private static final String TOKEN_URL = "https://oauth2.googleapis.com/token";
    private static final String USERINFO_URL = "https://www.googleapis.com/oauth2/v2/userinfo";
    private static final String SCOPES = "openid email profile";

    private final FormRepository formRepository;
    private final FormLoginService formLoginService;
    private final FormGoogleLoginCodeStore codeStore;
    private final RestClient restClient;
    private final String clientId;
    private final String clientSecret;
    private final String redirectUri;
    private final String frontendBaseUri;
    private final String stateSecret;

    public FormGoogleLoginService(
            FormRepository formRepository,
            FormLoginService formLoginService,
            FormGoogleLoginCodeStore codeStore,
            @Value("${app.google-form-login.client-id:}") String clientId,
            @Value("${app.google-form-login.client-secret:}") String clientSecret,
            @Value("${app.google-form-login.redirect-uri:http://localhost:5173/api/v1/public/forms/login/google/callback}")
                    String redirectUri,
            @Value("${app.oauth2.frontend-redirect-uri:http://localhost:5173/oauth2/redirect}")
                    String frontendRedirectUri,
            @Value("${jwt.secret}") String jwtSecret) {
        this.formRepository = formRepository;
        this.formLoginService = formLoginService;
        this.codeStore = codeStore;
        this.restClient = RestClient.create();
        this.clientId = clientId == null ? "" : clientId.trim();
        this.clientSecret = clientSecret == null ? "" : clientSecret.trim();
        this.redirectUri = redirectUri.trim();
        this.frontendBaseUri = frontendRedirectUri.replace("/oauth2/redirect", "");
        this.stateSecret = jwtSecret;
    }

    public boolean isConfigured() {
        return StringUtils.hasText(clientId) && StringUtils.hasText(clientSecret);
    }

    public String buildAuthorizeUrl(Long formId) {
        Form form = requirePublishedForm(formId);
        formLoginService.requireGoogleLoginAllowed(form);
        if (!isConfigured()) {
            return frontendCallbackUrl(false, formId, null, "not_configured");
        }
        String state = signState(formId);
        return UriComponentsBuilder.fromUriString(AUTHORIZE_URL)
                .queryParam("client_id", clientId)
                .queryParam("response_type", "code")
                .queryParam("redirect_uri", redirectUri)
                .queryParam("scope", SCOPES)
                .queryParam("state", state)
                .queryParam("prompt", "select_account")
                .encode()
                .build()
                .toUriString();
    }

    public String handleCallback(String code, String state, String error) {
        Long formId = null;
        try {
            if (StringUtils.hasText(state)) {
                formId = parseState(state);
            }
        } catch (RuntimeException ignored) {
            // handled below
        }
        if (error != null && !error.isBlank()) {
            return frontendCallbackUrl(false, formId, null, error);
        }
        if (!isConfigured()) {
            return frontendCallbackUrl(false, formId, null, "not_configured");
        }
        if (!StringUtils.hasText(code) || !StringUtils.hasText(state)) {
            return frontendCallbackUrl(false, formId, null, "oauth_failed");
        }
        try {
            formId = parseState(state);
            Form form = requirePublishedForm(formId);
            formLoginService.requireGoogleLoginAllowed(form);
            JsonNode token = exchangeToken(code);
            String accessToken = text(token, "access_token");
            if (!StringUtils.hasText(accessToken)) {
                return frontendCallbackUrl(false, formId, null, "oauth_failed");
            }
            JsonNode userinfo = fetchUserInfo(accessToken);
            if (!userinfo.path("verified_email").asBoolean(false)) {
                return frontendCallbackUrl(false, formId, null, "email_not_verified");
            }
            String email = text(userinfo, "email");
            if (!StringUtils.hasText(email)) {
                return frontendCallbackUrl(false, formId, null, "oauth_failed");
            }
            formLoginService.verifyGoogleEmail(form, email);
            String exchangeCode = codeStore.issue(formId, email);
            return frontendCallbackUrl(true, formId, exchangeCode, null);
        } catch (FormNotAvailableException | FormNotFoundException ex) {
            return frontendCallbackUrl(false, formId, null, "form_unavailable");
        } catch (IllegalArgumentException ex) {
            return frontendCallbackUrl(false, formId, null, "domain_not_allowed");
        } catch (RuntimeException ex) {
            return frontendCallbackUrl(false, formId, null, "oauth_failed");
        }
    }

    public FormLoginVerifyResponse exchange(Long formId, String code) {
        Form form = requirePublishedForm(formId);
        formLoginService.requireGoogleLoginAllowed(form);
        FormGoogleLoginCodeStore.Entry entry = codeStore.consume(code)
                .orElseThrow(() -> new IllegalArgumentException("Connexion Google expirée. Réessayez."));
        if (!formId.equals(entry.formId())) {
            throw new IllegalArgumentException("Connexion Google invalide pour ce formulaire.");
        }
        return formLoginService.verifyGoogleEmail(form, entry.email());
    }

    private Form requirePublishedForm(Long formId) {
        Form form = formRepository.findById(formId)
                .orElseThrow(() -> new FormNotFoundException(formId));
        if (form.getStatus() != FormStatus.PUBLISHED) {
            throw new FormNotAvailableException(formId);
        }
        return form;
    }

    private String frontendCallbackUrl(boolean ok, Long formId, String exchangeCode, String errorCode) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(
                frontendBaseUri + "/integrations/form-login/google/callback");
        if (ok) {
            builder.queryParam("ok", "1");
            builder.queryParam("code", exchangeCode);
            builder.queryParam("formId", formId);
        } else {
            builder.queryParam("ok", "0");
            if (formId != null) {
                builder.queryParam("formId", formId);
            }
            if (StringUtils.hasText(errorCode)) {
                builder.queryParam("error", errorCode);
            }
        }
        return builder.encode().build().toUriString();
    }

    private JsonNode exchangeToken(String code) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        form.add("code", code);
        form.add("redirect_uri", redirectUri);
        try {
            JsonNode body = restClient.post()
                    .uri(TOKEN_URL)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(JsonNode.class);
            if (body == null) {
                throw new IllegalStateException("Réponse Google vide.");
            }
            return body;
        } catch (RestClientException ex) {
            throw new IllegalStateException("Échec de la connexion Google.", ex);
        }
    }

    private JsonNode fetchUserInfo(String accessToken) {
        try {
            JsonNode body = restClient.get()
                    .uri(USERINFO_URL)
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .body(JsonNode.class);
            if (body == null) {
                throw new IllegalStateException("Réponse Google vide.");
            }
            return body;
        } catch (RestClientException ex) {
            throw new IllegalStateException("Impossible de lire le profil Google.", ex);
        }
    }

    private static String text(JsonNode node, String field) {
        String value = node.path(field).asText(null);
        return StringUtils.hasText(value) ? value : null;
    }

    private String signState(Long formId) {
        String nonce = UUID.randomUUID().toString().replace("-", "");
        String payload = formId + "." + Instant.now().getEpochSecond() + "." + nonce;
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString((payload + "." + hmac(payload)).getBytes(StandardCharsets.UTF_8));
    }

    private Long parseState(String state) {
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(state), StandardCharsets.UTF_8);
            String[] parts = decoded.split("\\.");
            if (parts.length != 4) {
                throw new IllegalArgumentException("État OAuth invalide.");
            }
            String payload = parts[0] + "." + parts[1] + "." + parts[2];
            if (!hmac(payload).equals(parts[3])) {
                throw new IllegalArgumentException("État OAuth invalide.");
            }
            long issued = Long.parseLong(parts[1]);
            if (Instant.now().getEpochSecond() - issued > 600) {
                throw new IllegalArgumentException("La connexion Google a expiré. Réessayez.");
            }
            return Long.parseLong(parts[0]);
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("État OAuth invalide.");
        }
    }

    private String hmac(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(stateSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("Impossible de signer l’état OAuth.", ex);
        }
    }
}
