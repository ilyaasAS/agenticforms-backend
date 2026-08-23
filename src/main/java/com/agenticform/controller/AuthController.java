package com.agenticform.controller;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.agenticform.dto.AuthResponse;
import com.agenticform.dto.AuthSessionResult;
import com.agenticform.dto.ChangePasswordRequest;
import com.agenticform.dto.DeleteAccountRequest;
import com.agenticform.dto.ForgotPasswordRequest;
import com.agenticform.dto.LocalLoginRequest;
import com.agenticform.dto.LocalLoginResponse;
import com.agenticform.dto.LoginRequest;
import com.agenticform.dto.MessageResponse;
import com.agenticform.dto.OAuth2LoginRequest;
import com.agenticform.dto.OAuth2LoginResponse;
import com.agenticform.dto.OAuthExchangeRequest;
import com.agenticform.dto.RegisterRequest;
import com.agenticform.dto.ResendVerificationRequest;
import com.agenticform.dto.ResetPasswordRequest;
import com.agenticform.dto.SignupRequest;
import com.agenticform.dto.SignupResponse;
import com.agenticform.dto.UpdateProfileRequest;
import com.agenticform.dto.UserProfileResponse;
import com.agenticform.dto.VerifyEmailRequest;
import com.agenticform.security.AuthCookieService;
import com.agenticform.security.UserPrincipal;
import com.agenticform.service.AuthService;
import com.agenticform.service.EmailVerificationService;
import com.agenticform.service.PasswordResetService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final AuthCookieService authCookieService;
    private final PasswordResetService passwordResetService;
    private final EmailVerificationService emailVerificationService;

    public AuthController(
            AuthService authService,
            AuthCookieService authCookieService,
            PasswordResetService passwordResetService,
            EmailVerificationService emailVerificationService) {
        this.authService = authService;
        this.authCookieService = authCookieService;
        this.passwordResetService = passwordResetService;
        this.emailVerificationService = emailVerificationService;
    }

    @PostMapping("/register")
    public ResponseEntity<MessageResponse> register(@Valid @RequestBody RegisterRequest request) {
        authService.register(request);
        // Toujours 202 neutre — ne confirme jamais si l'e-mail existe déjà.
        return ResponseEntity.accepted().body(new MessageResponse(
                "Si cet e-mail est disponible, un message de confirmation vous a été envoyé."));
    }

    @PostMapping("/signup")
    public ResponseEntity<SignupResponse> signup(@Valid @RequestBody SignupRequest request) {
        SignupResponse response = authService.signup(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        AuthSessionResult session = authService.login(request);
        authCookieService.setAccessToken(httpRequest, httpResponse, session.accessToken());
        return ResponseEntity.ok(session.response());
    }

    @PostMapping("/login/local")
    public ResponseEntity<LocalLoginResponse> loginLocal(
            @Valid @RequestBody LocalLoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        AuthSessionResult session = authService.loginLocal(request);
        authCookieService.setAccessToken(httpRequest, httpResponse, session.accessToken());
        return ResponseEntity.ok(authService.toLocalLoginResponse(session));
    }

    @PostMapping("/oauth/exchange")
    public ResponseEntity<AuthResponse> exchangeOAuthCode(
            @Valid @RequestBody OAuthExchangeRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        AuthSessionResult session = authService.exchangeOAuthCode(request.code());
        authCookieService.setAccessToken(httpRequest, httpResponse, session.accessToken());
        return ResponseEntity.ok(session.response());
    }

    @PostMapping("/login/oauth2")
    public ResponseEntity<OAuth2LoginResponse> loginOAuth2(
            @Valid @RequestBody OAuth2LoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        AuthSessionResult session = authService.loginOAuth2(request);
        authCookieService.setAccessToken(httpRequest, httpResponse, session.accessToken());
        return ResponseEntity.ok(authService.toOAuth2LoginResponse(session));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @AuthenticationPrincipal UserPrincipal principal,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        if (principal != null) {
            authService.revokeSessions(principal.getId());
        }
        authCookieService.clearAccessToken(httpRequest, httpResponse);
        SecurityContextHolder.clearContext();
        HttpSession session = httpRequest.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/csrf")
    public ResponseEntity<Map<String, String>> csrf(CsrfToken csrfToken) {
        return ResponseEntity.ok(Map.of(
                "headerName", csrfToken.getHeaderName(),
                "parameterName", csrfToken.getParameterName(),
                "token", csrfToken.getToken()));
    }

    @GetMapping("/me")
    public ResponseEntity<UserProfileResponse> me(@AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(authService.me(principal));
    }

    @PutMapping("/me")
    public ResponseEntity<UserProfileResponse> updateProfile(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody UpdateProfileRequest request) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(authService.updateProfile(principal, request));
    }

    @DeleteMapping("/oauth/{provider}")
    public ResponseEntity<UserProfileResponse> unlinkOAuthProvider(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String provider) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(authService.unlinkOAuthProvider(principal, provider));
    }

    @PostMapping("/change-password")
    public ResponseEntity<MessageResponse> changePassword(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody ChangePasswordRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        AuthSessionResult session = authService.changePassword(principal, request);
        authCookieService.setAccessToken(httpRequest, httpResponse, session.accessToken());
        return ResponseEntity.ok(new MessageResponse("Mot de passe mis à jour."));
    }

    @DeleteMapping("/account")
    public ResponseEntity<Void> deleteAccount(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody DeleteAccountRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        authService.deleteAccount(principal, request.confirmEmail());
        authCookieService.clearAccessToken(httpRequest, httpResponse);
        SecurityContextHolder.clearContext();
        HttpSession session = httpRequest.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/verify-email")
    public ResponseEntity<MessageResponse> verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        emailVerificationService.verify(request.token());
        return ResponseEntity.ok(new MessageResponse(
                "Adresse e-mail confirmée. Vous pouvez vous connecter."));
    }

    @PostMapping("/resend-verification")
    public ResponseEntity<MessageResponse> resendVerification(
            @Valid @RequestBody ResendVerificationRequest request) {
        emailVerificationService.resend(request);
        return ResponseEntity.ok(new MessageResponse(
                "Si un compte non vérifié existe avec cet e-mail, un nouveau lien a été envoyé."));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<MessageResponse> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        passwordResetService.requestPasswordReset(request);
        return ResponseEntity.ok(new MessageResponse(
                "Si un compte existe avec cet e-mail, un lien de réinitialisation a été envoyé."));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<MessageResponse> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        passwordResetService.resetPassword(request);
        return ResponseEntity.ok(new MessageResponse("Mot de passe mis à jour. Vous pouvez vous connecter."));
    }
}
