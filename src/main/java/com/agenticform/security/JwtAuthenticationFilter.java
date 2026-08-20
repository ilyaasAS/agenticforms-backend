package com.agenticform.security;

import java.io.IOException;

import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final CustomUserDetailsService userDetailsService;
    private final AuthCookieService authCookieService;

    public JwtAuthenticationFilter(
            JwtTokenProvider jwtTokenProvider,
            CustomUserDetailsService userDetailsService,
            AuthCookieService authCookieService) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.userDetailsService = userDetailsService;
        this.authCookieService = authCookieService;
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.contains("/oauth2/") || path.contains("/login/oauth2/");
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        String token = resolveToken(request);

        if (!StringUtils.hasText(token)) {
            filterChain.doFilter(request, response);
            return;
        }

        if (!jwtTokenProvider.validateToken(token)) {
            if (isPublicAuthEndpoint(request)) {
                // JWT expiré/invalide ne doit pas bloquer CSRF / login / register.
                authCookieService.clearAccessToken(request, response);
                filterChain.doFilter(request, response);
                return;
            }
            writeUnauthorized(response);
            return;
        }

        try {
            if (SecurityContextHolder.getContext().getAuthentication() == null) {
                Long userId = jwtTokenProvider.getUserIdFromToken(token);
                int tokenVersion = jwtTokenProvider.getTokenVersionFromToken(token);
                UserDetails userDetails = userDetailsService.loadUserById(userId);

                if (userDetails instanceof UserPrincipal principal
                        && principal.getTokenVersion() != tokenVersion) {
                    authCookieService.clearAccessToken(request, response);
                    if (isPublicAuthEndpoint(request)) {
                        filterChain.doFilter(request, response);
                        return;
                    }
                    writeUnauthorized(response);
                    return;
                }

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                userDetails,
                                null,
                                userDetails.getAuthorities());
                authentication.setDetails(
                        new org.springframework.security.web.authentication.WebAuthenticationDetailsSource()
                                .buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        } catch (RuntimeException ex) {
            SecurityContextHolder.clearContext();
            if (isPublicAuthEndpoint(request)) {
                authCookieService.clearAccessToken(request, response);
                filterChain.doFilter(request, response);
                return;
            }
            writeUnauthorized(response);
            return;
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Endpoints accessibles sans session valide (y compris cookie JWT expiré).
     * Inclut GET /api/auth/csrf — indispensable pour re-login / futur reset password.
     */
    private boolean isPublicAuthEndpoint(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path.contains("/oauth2/") || path.contains("/login/oauth2/")) {
            return true;
        }
        if (path.contains("/api/v1/public/forms/")) {
            return true;
        }
        if ("GET".equalsIgnoreCase(request.getMethod())
                && path.contains("/api/v1/integrations/calendly/callback")) {
            return true;
        }
        if ("GET".equalsIgnoreCase(request.getMethod())
                && path.contains("/api/v1/integrations/google-calendar/callback")) {
            return true;
        }
        if ("GET".equalsIgnoreCase(request.getMethod())
                && path.contains("/api/v1/integrations/stripe/callback")) {
            return true;
        }
        if (("GET".equalsIgnoreCase(request.getMethod()) || "HEAD".equalsIgnoreCase(request.getMethod()))
                && (path.contains("/api/v1/media/proxy")
                || path.contains("/api/v1/media/files/"))) {
            return true;
        }
        if ("GET".equalsIgnoreCase(request.getMethod()) && path.endsWith("/api/auth/csrf")) {
            return true;
        }
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return false;
        }
        return path.endsWith("/api/auth/login")
                || path.endsWith("/api/auth/register")
                || path.endsWith("/api/auth/oauth/exchange")
                || path.endsWith("/api/auth/logout")
                || path.endsWith("/api/auth/forgot-password")
                || path.endsWith("/api/auth/reset-password")
                || path.endsWith("/api/auth/resend-verification")
                || path.endsWith("/api/auth/verify-email");
    }

    private String resolveToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (AuthCookieService.ACCESS_COOKIE.equals(cookie.getName())
                        && StringUtils.hasText(cookie.getValue())) {
                    return cookie.getValue().trim();
                }
            }
        }
        return null;
    }

    private void writeUnauthorized(HttpServletResponse response) throws IOException {
        SecurityContextHolder.clearContext();
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(
                "{\"status\":401,\"error\":\"Session invalide ou expirée.\",\"message\":\"Session invalide ou expirée.\"}");
    }
}
