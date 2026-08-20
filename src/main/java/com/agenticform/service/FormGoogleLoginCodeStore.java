package com.agenticform.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

@Component
public class FormGoogleLoginCodeStore {

    private static final Duration TTL = Duration.ofSeconds(120);

    private final ConcurrentHashMap<String, Entry> codes = new ConcurrentHashMap<>();

    public record Entry(Long formId, String email, Instant expiresAt) {
    }

    public String issue(Long formId, String email) {
        purgeExpired();
        String code = UUID.randomUUID().toString().replace("-", "")
                + UUID.randomUUID().toString().replace("-", "");
        codes.put(code, new Entry(formId, email.trim().toLowerCase(), Instant.now().plus(TTL)));
        return code;
    }

    public Optional<Entry> consume(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        Entry entry = codes.remove(code.trim());
        if (entry == null || entry.expiresAt().isBefore(Instant.now())) {
            return Optional.empty();
        }
        return Optional.of(entry);
    }

    private void purgeExpired() {
        Instant now = Instant.now();
        for (Map.Entry<String, Entry> item : codes.entrySet()) {
            if (item.getValue().expiresAt().isBefore(now)) {
                codes.remove(item.getKey(), item.getValue());
            }
        }
    }
}
