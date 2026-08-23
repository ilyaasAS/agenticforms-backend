package com.agenticform.service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Vérifie un token Google reCAPTCHA v2 auprès de siteverify.
 */
@Service
public class RecaptchaService {

    private static final Logger log = LoggerFactory.getLogger(RecaptchaService.class);
    private static final String SITEVERIFY_URL = "https://www.google.com/recaptcha/api/siteverify";
    /** Secret de test Google (paire de la clé site de test). */
    private static final String GOOGLE_TEST_SECRET = "6LeIxAcTAAAAAGG-vFI1TnRWxMZNFuojJ4WifJWe";

    private final String secretKey;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public RecaptchaService(
            @Value("${app.recaptcha.secret-key:}") String secretKey,
            ObjectMapper objectMapper) {
        this.secretKey = secretKey == null || secretKey.isBlank() ? GOOGLE_TEST_SECRET : secretKey.trim();
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    public boolean verify(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        try {
            String body = "secret=" + URLEncoder.encode(secretKey, StandardCharsets.UTF_8)
                    + "&response=" + URLEncoder.encode(token.trim(), StandardCharsets.UTF_8);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(SITEVERIFY_URL))
                    .timeout(Duration.ofSeconds(8))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("reCAPTCHA siteverify HTTP {}", response.statusCode());
                return false;
            }
            Map<String, Object> json = objectMapper.readValue(
                    response.body(), new TypeReference<Map<String, Object>>() {});
            Object success = json.get("success");
            return Boolean.TRUE.equals(success);
        } catch (Exception ex) {
            log.warn("Échec vérification reCAPTCHA: {}", ex.getMessage());
            return false;
        }
    }
}
