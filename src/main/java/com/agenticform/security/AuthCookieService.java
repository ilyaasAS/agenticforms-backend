package com.agenticform.security;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Cookie HttpOnly pour le JWT d'accès (anti-XSS vs localStorage).
 * Max-Age aligné sur {@code jwt.expiration-ms} (TTL court) ;
 * {@link #clearAccessToken} invalide immédiatement le cookie côté client (logout / JWT expiré).
 */
@Component
public class AuthCookieService {

    public static final String ACCESS_COOKIE = "AF_ACCESS";

    private final Duration maxAge;
    private final CookieSecuritySupport cookieSecuritySupport;

    public AuthCookieService(
            @Value("${jwt.expiration-ms:3600000}") long expirationMs,
            CookieSecuritySupport cookieSecuritySupport) {
        this.maxAge = Duration.ofMillis(Math.max(expirationMs, 60_000L));
        this.cookieSecuritySupport = cookieSecuritySupport;
    }

    public void setAccessToken(HttpServletRequest request, HttpServletResponse response, String jwt) {
        response.addHeader(HttpHeaders.SET_COOKIE, buildCookie(request, jwt, maxAge).toString());
    }

    /** Invalide le cookie d'accès (logout ou JWT invalide sur endpoint public). */
    public void clearAccessToken(HttpServletRequest request, HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, buildCookie(request, "", Duration.ZERO).toString());
    }

    private ResponseCookie buildCookie(HttpServletRequest request, String value, Duration age) {
        return ResponseCookie.from(ACCESS_COOKIE, value == null ? "" : value)
                .httpOnly(true)
                .secure(cookieSecuritySupport.isSecureRequest(request))
                .sameSite("Lax")
                .path("/")
                .maxAge(age)
                .build();
    }
}
