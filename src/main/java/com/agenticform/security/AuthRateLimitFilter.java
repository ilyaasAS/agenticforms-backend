package com.agenticform.security;

import java.io.IOException;
import java.time.Duration;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Rate limiting mémoire sur les endpoints auth sensibles.
 * <p>
 * Préparé pour une transition distribuée (Redis / Nginx {@code limit_req}) :
 * la clé est stable ({@code remoteAddr|route}) et ne lit jamais {@code X-Forwarded-For}
 * spoofable. En multi-instances, remplacer {@link #counters} par un store partagé.
 */
@Component
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private static final Duration WINDOW = Duration.ofMinutes(1);
    private static final int MAX_KEYS = 10_000;

    private static final int LIMIT_LOGIN = 10;
    private static final int LIMIT_REGISTER = 5;
    private static final int LIMIT_EXCHANGE = 20;
    private static final int LIMIT_FORGOT_PASSWORD = 5;
    private static final int LIMIT_RESET_PASSWORD = 10;
    private static final int LIMIT_RESEND_VERIFICATION = 5;
    private static final int LIMIT_VERIFY_EMAIL = 20;

    /** Store in-memory — à remplacer par Redis pour un déploiement multi-instances. */
    private final ConcurrentHashMap<String, WindowCounter> counters = new ConcurrentHashMap<>();

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String path = normalizedPath(request);
        return !(path.endsWith("/api/auth/login")
                || path.endsWith("/api/auth/login/local")
                || path.endsWith("/api/auth/register")
                || path.endsWith("/api/auth/signup")
                || path.endsWith("/api/auth/oauth/exchange")
                || path.endsWith("/api/auth/login/oauth2")
                || path.endsWith("/api/auth/forgot-password")
                || path.endsWith("/api/auth/reset-password")
                || path.endsWith("/api/auth/resend-verification")
                || path.endsWith("/api/auth/verify-email"));
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        purgeExpired();
        evictIfOverCapacity();

        String path = normalizedPath(request);
        int limit = resolveLimit(path);
        String key = clientKey(request) + "|" + routeBucket(path);

        WindowCounter counter = counters.computeIfAbsent(key, k -> new WindowCounter());
        if (!counter.tryAcquire(limit)) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setHeader("Retry-After", "60");
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(
                    "{\"status\":429,\"error\":\"Trop de tentatives. Réessayez plus tard.\",\"message\":\"Trop de tentatives. Réessayez plus tard.\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private static int resolveLimit(String path) {
        if (path.endsWith("/api/auth/register") || path.endsWith("/api/auth/signup")) {
            return LIMIT_REGISTER;
        }
        if (path.endsWith("/api/auth/oauth/exchange") || path.endsWith("/api/auth/login/oauth2")) {
            return LIMIT_EXCHANGE;
        }
        if (path.endsWith("/api/auth/login/local")) {
            return LIMIT_LOGIN;
        }
        if (path.endsWith("/api/auth/forgot-password")) {
            return LIMIT_FORGOT_PASSWORD;
        }
        if (path.endsWith("/api/auth/reset-password")) {
            return LIMIT_RESET_PASSWORD;
        }
        if (path.endsWith("/api/auth/resend-verification")) {
            return LIMIT_RESEND_VERIFICATION;
        }
        if (path.endsWith("/api/auth/verify-email")) {
            return LIMIT_VERIFY_EMAIL;
        }
        return LIMIT_LOGIN;
    }

    private static String routeBucket(String path) {
        int idx = path.indexOf("/api/auth/");
        return idx >= 0 ? path.substring(idx) : path;
    }

    private static String normalizedPath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri == null || uri.isBlank()) {
            return "";
        }
        // Ignore query string / trailing slash variations
        int q = uri.indexOf('?');
        if (q >= 0) {
            uri = uri.substring(0, q);
        }
        if (uri.length() > 1 && uri.endsWith("/")) {
            uri = uri.substring(0, uri.length() - 1);
        }
        return uri;
    }

    /**
     * Clé IP basée uniquement sur remoteAddr (pas de X-Forwarded-For client spoofable).
     * Derrière nginx, configurer Tomcat RemoteIpValve (proxies de confiance) pour que
     * remoteAddr reflète le client réel.
     */
    private String clientKey(HttpServletRequest request) {
        String remote = request.getRemoteAddr();
        return remote == null || remote.isBlank() ? "unknown" : remote.trim();
    }

    private void purgeExpired() {
        long cutoff = System.currentTimeMillis() - WINDOW.toMillis();
        Iterator<Map.Entry<String, WindowCounter>> it = counters.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, WindowCounter> entry = it.next();
            if (entry.getValue().windowStartMs < cutoff) {
                it.remove();
            }
        }
    }

    /** Plafond anti-DoS mémoire (épuisement de clés uniques). */
    private void evictIfOverCapacity() {
        if (counters.size() <= MAX_KEYS) {
            return;
        }
        Iterator<Map.Entry<String, WindowCounter>> it = counters.entrySet().iterator();
        int toRemove = counters.size() - MAX_KEYS + (MAX_KEYS / 10);
        while (it.hasNext() && toRemove > 0) {
            it.next();
            it.remove();
            toRemove--;
        }
    }

    private static final class WindowCounter {
        private long windowStartMs = System.currentTimeMillis();
        private final AtomicInteger count = new AtomicInteger(0);

        synchronized boolean tryAcquire(int limit) {
            long now = System.currentTimeMillis();
            if (now - windowStartMs >= WINDOW.toMillis()) {
                windowStartMs = now;
                count.set(0);
            }
            return count.incrementAndGet() <= limit;
        }
    }
}
