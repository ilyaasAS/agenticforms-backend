package com.agenticform.security;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.WebUtils;

import com.agenticform.exception.OAuthEmailNotVerifiedException;
import com.agenticform.exception.OAuthIdentityConflictException;
import com.agenticform.exception.OAuthLinkRequiresVerifiedEmailException;
import com.agenticform.model.entity.AuthProvider;
import com.agenticform.model.entity.User;
import com.agenticform.service.OAuthUserResult;
import com.agenticform.service.OAuthUserService;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

@Component
public class OAuth2AuthenticationSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private static final Logger log = LoggerFactory.getLogger(OAuth2AuthenticationSuccessHandler.class);

    private final OAuthUserService oauthUserService;
    private final JwtTokenProvider jwtTokenProvider;
    private final OAuthCodeStore oauthCodeStore;
    private final CookieSecuritySupport cookieSecuritySupport;
    private final String frontendRedirectUri;
    private final String frontendLoginUri;
    private final String frontendRegisterUri;

    public OAuth2AuthenticationSuccessHandler(
            OAuthUserService oauthUserService,
            JwtTokenProvider jwtTokenProvider,
            OAuthCodeStore oauthCodeStore,
            CookieSecuritySupport cookieSecuritySupport,
            @Value("${app.oauth2.frontend-redirect-uri:http://localhost:5173/oauth2/redirect}")
            String frontendRedirectUri) {
        this.oauthUserService = oauthUserService;
        this.jwtTokenProvider = jwtTokenProvider;
        this.oauthCodeStore = oauthCodeStore;
        this.cookieSecuritySupport = cookieSecuritySupport;
        this.frontendRedirectUri = frontendRedirectUri;
        this.frontendLoginUri = frontendRedirectUri.replace("/oauth2/redirect", "/login");
        this.frontendRegisterUri = frontendRedirectUri.replace("/oauth2/redirect", "/register");
        setDefaultTargetUrl(frontendRedirectUri);
    }

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication) throws IOException {

        try {
            if (!(authentication instanceof OAuth2AuthenticationToken oauthToken)
                    || !(authentication.getPrincipal() instanceof OAuth2User oauth2User)) {
                log.warn("OAuth2 success with unexpected principal type: {}",
                        authentication == null ? null : authentication.getClass().getName());
                redirectToLoginError(request, response, "oauth_failed");
                return;
            }

            AuthProvider provider = mapProvider(oauthToken.getAuthorizedClientRegistrationId());
            String registrationId = oauthToken.getAuthorizedClientRegistrationId();
            String email = OAuth2UserAttributes.extractEmail(oauth2User);
            String fullName = OAuth2UserAttributes.extractFullName(oauth2User);
            String subject = OAuth2UserAttributes.extractSubject(oauth2User);
            String intent = resolveIntent(request);
            boolean emailVerified = OAuth2UserAttributes.isEmailVerified(oauth2User, provider, email);

            if (subject == null || subject.isBlank()) {
                log.warn("OAuth2 rejected: missing IdP subject. registrationId={}", registrationId);
                redirectToLoginError(request, response, "oauth_failed");
                return;
            }

            if (email == null || email.isBlank()) {
                log.warn("OAuth2 user has no email. registrationId={}", registrationId);
                redirectToLoginError(request, response, "oauth_failed");
                return;
            }

            if (!emailVerified) {
                log.warn("OAuth2 rejected: email not verified ({}) registrationId={}", email, registrationId);
                redirectToLoginError(request, response, "email_not_verified");
                return;
            }

            if (OAuthIntent.SIGNUP.equals(intent) && oauthUserService.findByEmail(email).isPresent()) {
                log.info("OAuth2 signup blocked: email already registered");
                redirectAlreadyRegistered(request, response);
                return;
            }

            OAuthUserResult result = oauthUserService.findOrCreateOAuthUser(
                    email, fullName, provider, subject, emailVerified);
            User user = result.user();
            String jwt = jwtTokenProvider.generateToken(user);
            String oneTimeCode = oauthCodeStore.issue(jwt);

            String targetUrl = UriComponentsBuilder
                    .fromUriString(frontendRedirectUri)
                    .queryParam("code", oneTimeCode)
                    .build()
                    .encode()
                    .toUriString();

            log.info("OAuth2 {} success provider={}, subBound, created={}",
                    intent, provider, result.created());

            invalidateOAuthHandshake(request, response);
            getRedirectStrategy().sendRedirect(request, response, targetUrl);
        } catch (OAuthEmailNotVerifiedException ex) {
            log.warn("OAuth2 email not verified");
            redirectToLoginError(request, response, "email_not_verified");
        } catch (OAuthLinkRequiresVerifiedEmailException ex) {
            log.warn("OAuth2 link blocked: local email not verified");
            redirectToLoginError(request, response, "verify_email_required");
        } catch (OAuthIdentityConflictException ex) {
            log.warn("OAuth2 identity conflict");
            redirectToLoginError(request, response, "oauth_conflict");
        } catch (Exception ex) {
            log.error("OAuth2 success handling failed", ex);
            redirectToLoginError(request, response, "oauth_failed");
        }
    }

    private String resolveIntent(HttpServletRequest request) {
        Cookie cookie = WebUtils.getCookie(request, OAuthIntent.COOKIE_NAME);
        if (cookie != null && cookie.getValue() != null && !cookie.getValue().isBlank()) {
            return OAuthIntent.normalize(cookie.getValue());
        }

        HttpSession session = request.getSession(false);
        if (session != null) {
            Object value = session.getAttribute(OAuthIntent.SESSION_ATTRIBUTE);
            if (value instanceof String intent && !intent.isBlank()) {
                return OAuthIntent.normalize(intent);
            }
        }
        return OAuthIntent.LOGIN;
    }

    private void clearIntentCookie(HttpServletRequest request, HttpServletResponse response) {
        ResponseCookie expired = ResponseCookie.from(OAuthIntent.COOKIE_NAME, "")
                .path("/")
                .maxAge(0)
                .httpOnly(true)
                .secure(cookieSecuritySupport.isSecureRequest(request))
                .sameSite("Lax")
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, expired.toString());
    }

    private void redirectAlreadyRegistered(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        invalidateOAuthHandshake(request, response);
        String targetUrl = UriComponentsBuilder
                .fromUriString(frontendRegisterUri)
                .queryParam("error", "already_registered")
                .build()
                .encode()
                .toUriString();
        getRedirectStrategy().sendRedirect(request, response, targetUrl);
    }

    private void redirectToLoginError(
            HttpServletRequest request,
            HttpServletResponse response,
            String errorCode) throws IOException {
        invalidateOAuthHandshake(request, response);
        String targetUrl = UriComponentsBuilder
                .fromUriString(frontendLoginUri)
                .queryParam("error", errorCode)
                .build()
                .encode()
                .toUriString();
        getRedirectStrategy().sendRedirect(request, response, targetUrl);
    }

    private void invalidateOAuthHandshake(HttpServletRequest request, HttpServletResponse response) {
        clearIntentCookie(request, response);
        clearAuthenticationAttributes(request);
        SecurityContextHolder.clearContext();
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
    }

    private AuthProvider mapProvider(String registrationId) {
        if ("google".equalsIgnoreCase(registrationId)) {
            return AuthProvider.GOOGLE;
        }
        if ("azure".equalsIgnoreCase(registrationId)) {
            return AuthProvider.AZURE;
        }
        throw new IllegalStateException("Unknown OAuth registration: " + registrationId);
    }

}
