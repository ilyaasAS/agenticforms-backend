package com.agenticform.service;

import java.util.Comparator;
import java.util.List;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.agenticform.dto.AuthResponse;
import com.agenticform.dto.AuthSessionResult;
import com.agenticform.dto.LocalLoginRequest;
import com.agenticform.dto.LocalLoginResponse;
import com.agenticform.dto.LoginRequest;
import com.agenticform.dto.OAuth2LoginRequest;
import com.agenticform.dto.OAuth2LoginResponse;
import com.agenticform.dto.RegisterRequest;
import com.agenticform.dto.SignupRequest;
import com.agenticform.dto.SignupResponse;
import com.agenticform.dto.UserProfileResponse;
import com.agenticform.exception.InvalidOAuthCodeException;
import com.agenticform.exception.LastAuthMethodException;
import com.agenticform.exception.OAuthLinkNotFoundException;
import com.agenticform.model.entity.AuthProvider;
import com.agenticform.model.entity.Role;
import com.agenticform.model.entity.User;
import com.agenticform.model.entity.UserOAuthAccount;
import com.agenticform.repository.UserOAuthAccountRepository;
import com.agenticform.repository.UserRepository;
import com.agenticform.security.JwtTokenProvider;
import com.agenticform.security.OAuthCodeStore;
import com.agenticform.security.UserPrincipal;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final UserOAuthAccountRepository oauthAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final AuthenticationManager authenticationManager;
    private final OAuthCodeStore oauthCodeStore;
    private final EmailVerificationService emailVerificationService;
    private final WorkspaceService workspaceService;

    public AuthService(
            UserRepository userRepository,
            UserOAuthAccountRepository oauthAccountRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenProvider jwtTokenProvider,
            AuthenticationManager authenticationManager,
            OAuthCodeStore oauthCodeStore,
            EmailVerificationService emailVerificationService,
            WorkspaceService workspaceService) {
        this.userRepository = userRepository;
        this.oauthAccountRepository = oauthAccountRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.authenticationManager = authenticationManager;
        this.oauthCodeStore = oauthCodeStore;
        this.emailVerificationService = emailVerificationService;
        this.workspaceService = workspaceService;
    }

    /**
     * Inscription locale : pas de session JWT tant que l'e-mail n'est pas vérifié (E-3).
     * Anti-énumération : si l'e-mail existe déjà, no-op silencieux (réponse API neutre).
     */
    @Transactional
    public void register(RegisterRequest request) {
        String email = normalizeEmail(request.email());

        if (userRepository.existsByEmail(email)) {
            // Coût comparable (hash) pour limiter les fuites de timing.
            passwordEncoder.encode(request.password());
            return;
        }

        User user = new User();
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setRole(Role.ROLE_USER);
        user.setPasswordEnabled(true);
        user.setEmailVerified(false);

        User saved = userRepository.save(user);
        workspaceService.createDefaultWorkspaceForUser(saved);
        emailVerificationService.issueAndSend(saved);
    }

    @Transactional
    public SignupResponse signup(SignupRequest request) {
        String email = normalizeEmail(request.email());

        if (userRepository.existsByEmail(email)) {
            passwordEncoder.encode(request.password());
            return new SignupResponse("Si cet e-mail est disponible, un message de confirmation vous a été envoyé.");
        }

        User user = new User();
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setFullName(request.fullName());
        user.setRole(Role.ROLE_USER);
        user.setPasswordEnabled(true);
        user.setEmailVerified(false);

        User saved = userRepository.save(user);
        workspaceService.createDefaultWorkspaceForUser(saved);
        emailVerificationService.issueAndSend(saved);
        return new SignupResponse("Si cet e-mail est disponible, un message de confirmation vous a été envoyé.");
    }

    /**
     * Login local. Tous les échecs (inconnu, OAuth-only, non vérifié, mauvais mot de passe)
     * remontent comme BadCredentialsException — réponse 401 générique côté handler.
     */
    public AuthSessionResult login(LoginRequest request) {
        String email = normalizeEmail(request.email());

        User existing = userRepository.findByEmail(email).orElse(null);
        if (existing != null && (!existing.isPasswordEnabled() || !existing.isEmailVerified())) {
            throw new BadCredentialsException("Invalid credentials");
        }

        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(email, request.password()));

        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found"));

        if (!user.isPasswordEnabled() || !user.isEmailVerified()) {
            throw new BadCredentialsException("Invalid credentials");
        }

        String token = jwtTokenProvider.generateToken(user);
        return toAuthSession(token, user);
    }

    public AuthSessionResult loginLocal(LocalLoginRequest request) {
        return login(new LoginRequest(request.email(), request.password()));
    }

    @Transactional(readOnly = true)
    public AuthSessionResult exchangeOAuthCode(String code) {
        String jwt = oauthCodeStore.consume(code)
                .orElseThrow(InvalidOAuthCodeException::new);

        if (!jwtTokenProvider.validateToken(jwt)) {
            throw new InvalidOAuthCodeException();
        }

        Long userId = jwtTokenProvider.getUserIdFromToken(jwt);
        User user = userRepository.findById(userId)
                .orElseThrow(InvalidOAuthCodeException::new);

        if (jwtTokenProvider.getTokenVersionFromToken(jwt) != user.getTokenVersion()) {
            throw new InvalidOAuthCodeException();
        }

        return toAuthSession(jwt, user);
    }

    public AuthSessionResult loginOAuth2(OAuth2LoginRequest request) {
        return exchangeOAuthCode(request.code());
    }

    /**
     * Invalide les JWT en cours pour cet utilisateur (logout / compromission).
     */
    @Transactional
    public void revokeSessions(Long userId) {
        if (userId == null) {
            return;
        }
        userRepository.findById(userId).ifPresent(user -> {
            user.setTokenVersion(user.getTokenVersion() + 1);
            userRepository.save(user);
        });
    }

    @Transactional(readOnly = true)
    public UserProfileResponse me(UserPrincipal principal) {
        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found"));
        return toProfile(user);
    }

    @Transactional
    public UserProfileResponse unlinkOAuthProvider(UserPrincipal principal, String providerParam) {
        String providerKey = normalizeOAuthProvider(providerParam);
        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found"));

        long linkedCount = oauthAccountRepository.countByUserId(user.getId());
        if (!user.isPasswordEnabled() && linkedCount <= 1) {
            throw new LastAuthMethodException();
        }

        UserOAuthAccount link = oauthAccountRepository
                .findByUserIdAndProvider(user.getId(), providerKey)
                .orElseThrow(() -> new OAuthLinkNotFoundException(providerKey));

        user.removeOAuthAccount(link);
        userRepository.save(user);
        return toProfile(user);
    }

    private UserProfileResponse toProfile(User user) {
        List<String> providers = user.getOauthAccounts().stream()
                .map(UserOAuthAccount::getProvider)
                .sorted(Comparator.naturalOrder())
                .toList();

        return new UserProfileResponse(
                user.getId(),
                user.getEmail(),
                user.getRole().name(),
                user.getFullName(),
                user.getCreatedAt(),
                user.isPasswordEnabled(),
                providers);
    }

    private AuthSessionResult toAuthSession(String accessToken, User user) {
        return new AuthSessionResult(
                accessToken,
                new AuthResponse(
                        new AuthResponse.UserInfo(
                                user.getId(),
                                user.getEmail(),
                                user.getRole().name(),
                                user.getFullName())));
    }

    public LocalLoginResponse toLocalLoginResponse(AuthSessionResult session) {
        return new LocalLoginResponse(session.response().user());
    }

    public OAuth2LoginResponse toOAuth2LoginResponse(AuthSessionResult session) {
        return new OAuth2LoginResponse(session.response().user());
    }

    private String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }

    private String normalizeOAuthProvider(String provider) {
        if (provider == null || provider.isBlank()) {
            throw new OAuthLinkNotFoundException("unknown");
        }
        String key = provider.trim().toUpperCase();
        if ("MICROSOFT".equals(key) || "AZURE_AD".equals(key) || "AZUREAD".equals(key)) {
            key = AuthProvider.AZURE.name();
        }
        if (!AuthProvider.GOOGLE.name().equals(key) && !AuthProvider.AZURE.name().equals(key)) {
            throw new OAuthLinkNotFoundException(provider);
        }
        return key;
    }
}
