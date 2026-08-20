package com.agenticform.config;

import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestRedirectFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.header.writers.XXssProtectionHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.agenticform.config.OAuth2ClientConfig.OAuth2ClientsAvailability;
import com.agenticform.security.AuthRateLimitFilter;
import com.agenticform.security.CsrfCookieFilter;
import com.agenticform.security.CustomUserDetailsService;
import com.agenticform.security.JwtAuthenticationFilter;
import com.agenticform.security.OAuth2AuthenticationFailureHandler;
import com.agenticform.security.OAuth2AuthenticationSuccessHandler;
import com.agenticform.security.OAuthIntentCaptureFilter;
import com.agenticform.security.SelectAccountOAuth2AuthorizationRequestResolver;
import com.agenticform.security.SpaCsrfTokenRequestHandler;

import jakarta.servlet.http.HttpServletResponse;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final AuthRateLimitFilter authRateLimitFilter;
    private final CsrfCookieFilter csrfCookieFilter;
    private final OAuthIntentCaptureFilter oauthIntentCaptureFilter;
    private final CustomUserDetailsService userDetailsService;
    private final OAuth2AuthenticationSuccessHandler oauth2AuthenticationSuccessHandler;
    private final OAuth2AuthenticationFailureHandler oauth2AuthenticationFailureHandler;
    private final SelectAccountOAuth2AuthorizationRequestResolver authorizationRequestResolver;
    private final OAuth2ClientsAvailability oauth2ClientsAvailability;
    private final PasswordEncoder passwordEncoder;
    private final List<String> allowedOrigins;

    public SecurityConfig(
            JwtAuthenticationFilter jwtAuthenticationFilter,
            AuthRateLimitFilter authRateLimitFilter,
            CsrfCookieFilter csrfCookieFilter,
            OAuthIntentCaptureFilter oauthIntentCaptureFilter,
            CustomUserDetailsService userDetailsService,
            OAuth2AuthenticationSuccessHandler oauth2AuthenticationSuccessHandler,
            OAuth2AuthenticationFailureHandler oauth2AuthenticationFailureHandler,
            SelectAccountOAuth2AuthorizationRequestResolver authorizationRequestResolver,
            OAuth2ClientsAvailability oauth2ClientsAvailability,
            PasswordEncoder passwordEncoder,
            @Value("${cors.allowed-origins:http://localhost:5173}") String allowedOrigins) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.authRateLimitFilter = authRateLimitFilter;
        this.csrfCookieFilter = csrfCookieFilter;
        this.oauthIntentCaptureFilter = oauthIntentCaptureFilter;
        this.userDetailsService = userDetailsService;
        this.oauth2AuthenticationSuccessHandler = oauth2AuthenticationSuccessHandler;
        this.oauth2AuthenticationFailureHandler = oauth2AuthenticationFailureHandler;
        this.authorizationRequestResolver = authorizationRequestResolver;
        this.oauth2ClientsAvailability = oauth2ClientsAvailability;
        this.passwordEncoder = passwordEncoder;
        this.allowedOrigins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty() && !"*".equals(origin))
                .toList();
    }

    @Bean
    @Order(1)
    public SecurityFilterChain oauth2SecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/oauth2/**", "/login/oauth2/**")
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .headers(this::applySecurityHeaders)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());

        if (oauth2ClientsAvailability.anyEnabled()) {
            http.oauth2Login(oauth2 -> oauth2
                    .authorizationEndpoint(authorization -> authorization
                            .authorizationRequestResolver(authorizationRequestResolver))
                    .successHandler(oauth2AuthenticationSuccessHandler)
                    .failureHandler(oauth2AuthenticationFailureHandler));
            http.addFilterBefore(oauthIntentCaptureFilter, OAuth2AuthorizationRequestRedirectFilter.class);
        }

        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain apiSecurityFilterChain(HttpSecurity http) throws Exception {
        CookieCsrfTokenRepository csrfTokenRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrfTokenRepository.setCookieCustomizer(cookie -> cookie
                .path("/")
                .sameSite("Lax")
                .httpOnly(false));

        http
                .securityMatcher("/api/**")
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfTokenRepository)
                        .csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler())
                        .ignoringRequestMatchers(
                                "/api/v1/public/forms/*/submissions",
                                "/api/v1/public/forms/*/session",
                                "/api/v1/public/forms/*/login/**",
                                "/api/v1/public/forms/*/scheduling/**"))
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(unauthorizedEntryPoint())
                        .accessDeniedHandler(accessDeniedHandler()))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/api/auth/csrf").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/public/forms/login/google/callback")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/public/forms/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/public/forms/*/submissions").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/public/forms/*/session").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/public/forms/*/login/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/public/forms/*/scheduling/**").permitAll()
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/public/forms/*/scheduling/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/public/forms/*/payments/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/integrations/calendly/callback")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/integrations/google-calendar/callback")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/integrations/stripe/callback")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/media/proxy").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/media/files/**").permitAll()
                        .requestMatchers(HttpMethod.HEAD, "/api/v1/media/files/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/media/upload").permitAll()
                        .requestMatchers(HttpMethod.POST,
                                "/api/auth/register",
                                "/api/auth/signup",
                                "/api/auth/login",
                                "/api/auth/login/local",
                                "/api/auth/oauth/exchange",
                                "/api/auth/login/oauth2",
                                "/api/auth/logout",
                                "/api/auth/forgot-password",
                                "/api/auth/reset-password",
                                "/api/auth/resend-verification",
                                "/api/auth/verify-email").permitAll()
                        .anyRequest().authenticated())
                .headers(this::applySecurityHeaders)
                .authenticationProvider(authenticationProvider())
                .addFilterBefore(authRateLimitFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(csrfCookieFilter, CsrfFilter.class);

        return http.build();
    }

    @Bean
    @Order(3)
    public SecurityFilterChain defaultSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .headers(this::applySecurityHeaders)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/error", "/error/**").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .anyRequest().denyAll());
        return http.build();
    }

    private void applySecurityHeaders(
            org.springframework.security.config.annotation.web.configurers.HeadersConfigurer<HttpSecurity> headers) {
        headers
                .contentTypeOptions(Customizer.withDefaults())
                .frameOptions(frame -> frame.sameOrigin())
                .httpStrictTransportSecurity(hsts -> hsts
                        .includeSubDomains(true)
                        .preload(true)
                        .maxAgeInSeconds(31_536_000))
                .referrerPolicy(referrer -> referrer
                        .policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                .xssProtection(xss -> xss
                        .headerValue(XXssProtectionHeaderWriter.HeaderValue.ENABLED_MODE_BLOCK));
        headers.permissionsPolicy(permissions -> permissions
                .policy("camera=(), microphone=(), geolocation=()"));
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(passwordEncoder);
        provider.setUserDetailsService(userDetailsService);
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration)
            throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    public AuthenticationEntryPoint unauthorizedEntryPoint() {
        return (request, response, authException) -> {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(
                    "{\"status\":401,\"error\":\"Authentification requise.\",\"message\":\"Authentification requise.\"}");
        };
    }

    @Bean
    public AccessDeniedHandler accessDeniedHandler() {
        return (request, response, accessDeniedException) -> {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            boolean csrf = accessDeniedException.getMessage() != null
                    && accessDeniedException.getMessage().toLowerCase().contains("csrf");
            String message = csrf
                    ? "Jeton CSRF invalide. Rechargez la page et réessayez."
                    : "Accès refusé.";
            response.getWriter().write(
                    "{\"status\":403,\"error\":\"" + message + "\",\"message\":\"" + message + "\"}");
        };
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        List<String> origins = allowedOrigins.isEmpty()
                ? List.of("http://localhost:5173")
                : allowedOrigins;
        config.setAllowedOrigins(origins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of(
                "Authorization",
                "Content-Type",
                "X-XSRF-TOKEN",
                "X-Requested-With"));
        config.setExposedHeaders(List.of("Authorization"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
