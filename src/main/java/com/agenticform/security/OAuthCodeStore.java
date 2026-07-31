package com.agenticform.security;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/**
 * Codes OAuth à usage unique (remplace le JWT dans l'URL de redirect).
 */
@Component
public class OAuthCodeStore {

    private static final Duration TTL = Duration.ofSeconds(120);

    private final ConcurrentHashMap<String, Entry> codes = new ConcurrentHashMap<>();

    public String issue(String jwt) {
        purgeExpired();
        String code = UUID.randomUUID().toString().replace("-", "")
                + UUID.randomUUID().toString().replace("-", "");
        codes.put(code, new Entry(jwt, Instant.now().plus(TTL)));
        return code;
    }

    public Optional<String> consume(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        Entry entry = codes.remove(code.trim());
        if (entry == null || entry.expiresAt().isBefore(Instant.now())) {
            return Optional.empty();
        }
        return Optional.of(entry.jwt());
    }

    private void purgeExpired() {
        Instant now = Instant.now();
        for (Map.Entry<String, Entry> e : codes.entrySet()) {
            if (e.getValue().expiresAt().isBefore(now)) {
                codes.remove(e.getKey(), e.getValue());
            }
        }
    }

    private record Entry(String jwt, Instant expiresAt) {
    }
}
