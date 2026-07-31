package com.agenticform.config;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.agenticform.security.AuthRateLimitFilter;
import com.agenticform.security.CsrfCookieFilter;
import com.agenticform.security.JwtAuthenticationFilter;
import com.agenticform.security.OAuthIntentCaptureFilter;

/**
 * Empêche l'auto-enregistrement servlet des filtres déjà branchés
 * dans les {@code SecurityFilterChain} (évite double exécution / ordre ambigu).
 */
@Configuration
public class SecurityFilterRegistrationConfig {

    @Bean
    public FilterRegistrationBean<JwtAuthenticationFilter> jwtAuthenticationFilterRegistration(
            JwtAuthenticationFilter filter) {
        return disabled(filter);
    }

    @Bean
    public FilterRegistrationBean<AuthRateLimitFilter> authRateLimitFilterRegistration(
            AuthRateLimitFilter filter) {
        return disabled(filter);
    }

    @Bean
    public FilterRegistrationBean<CsrfCookieFilter> csrfCookieFilterRegistration(
            CsrfCookieFilter filter) {
        return disabled(filter);
    }

    @Bean
    public FilterRegistrationBean<OAuthIntentCaptureFilter> oauthIntentCaptureFilterRegistration(
            OAuthIntentCaptureFilter filter) {
        return disabled(filter);
    }

    private static <T extends jakarta.servlet.Filter> FilterRegistrationBean<T> disabled(T filter) {
        FilterRegistrationBean<T> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }
}
