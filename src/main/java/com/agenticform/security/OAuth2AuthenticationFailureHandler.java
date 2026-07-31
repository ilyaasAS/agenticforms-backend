package com.agenticform.security;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

@Component
public class OAuth2AuthenticationFailureHandler implements AuthenticationFailureHandler {

    private static final Logger log = LoggerFactory.getLogger(OAuth2AuthenticationFailureHandler.class);

    private final String frontendLoginUri;
    private final CookieSecuritySupport cookieSecuritySupport;

    public OAuth2AuthenticationFailureHandler(
            CookieSecuritySupport cookieSecuritySupport,
            @Value("${app.oauth2.frontend-redirect-uri:http://localhost:5173/oauth2/redirect}")
            String frontendRedirectUri) {
        this.cookieSecuritySupport = cookieSecuritySupport;
        this.frontendLoginUri = frontendRedirectUri.replace("/oauth2/redirect", "/login");
    }

    @Override
    public void onAuthenticationFailure(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception) throws IOException {
        log.warn("OAuth2 authentication failed: {}", exception.getMessage());

        SecurityContextHolder.clearContext();
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }

        ResponseCookie clearIntent = ResponseCookie.from(OAuthIntent.COOKIE_NAME, "")
                .path("/")
                .maxAge(0)
                .httpOnly(true)
                .secure(cookieSecuritySupport.isSecureRequest(request))
                .sameSite("Lax")
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, clearIntent.toString());

        String targetUrl = UriComponentsBuilder
                .fromUriString(frontendLoginUri)
                .queryParam("error", "oauth_failed")
                .build()
                .encode()
                .toUriString();

        response.sendRedirect(targetUrl);
    }
}
