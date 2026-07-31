package com.agenticform.security;

import java.io.IOException;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * Capture intent=signup|login au démarrage OAuth et le persiste (cookie + session).
 */
@Component
public class OAuthIntentCaptureFilter extends OncePerRequestFilter {

    private final CookieSecuritySupport cookieSecuritySupport;

    public OAuthIntentCaptureFilter(CookieSecuritySupport cookieSecuritySupport) {
        this.cookieSecuritySupport = cookieSecuritySupport;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path == null || !path.contains("/oauth2/authorization/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String intent = OAuthIntent.normalize(request.getParameter("intent"));

        HttpSession session = request.getSession(true);
        session.setAttribute(OAuthIntent.SESSION_ATTRIBUTE, intent);

        ResponseCookie cookie = ResponseCookie.from(OAuthIntent.COOKIE_NAME, intent)
                .path("/")
                .maxAge(600)
                .httpOnly(true)
                .secure(cookieSecuritySupport.isSecureRequest(request))
                .sameSite("Lax")
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());

        filterChain.doFilter(request, response);
    }
}
