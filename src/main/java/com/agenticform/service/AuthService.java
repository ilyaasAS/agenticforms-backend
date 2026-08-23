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
import com.agenticform.dto.ChangePasswordRequest;
import com.agenticform.dto.LocalLoginRequest;
import com.agenticform.dto.LocalLoginResponse;
import com.agenticform.dto.LoginRequest;
import com.agenticform.dto.OAuth2LoginRequest;
import com.agenticform.dto.OAuth2LoginResponse;
import com.agenticform.dto.RegisterRequest;
import com.agenticform.dto.SignupRequest;
import com.agenticform.dto.SignupResponse;
import com.agenticform.dto.UpdateProfileRequest;
import com.agenticform.dto.UserProfileResponse;
import com.agenticform.exception.AccountDeleteConfirmationException;
import com.agenticform.exception.InvalidCurrentPasswordException;
import com.agenticform.exception.InvalidOAuthCodeException;
import com.agenticform.exception.LastAuthMethodException;
import com.agenticform.exception.OAuthLinkNotFoundException;
import com.agenticform.exception.SamePasswordException;
import com.agenticform.model.entity.AuthProvider;
import com.agenticform.model.entity.User;
import com.agenticform.model.entity.UserOAuthAccount;
import com.agenticform.model.entity.WorkspaceMember;
import com.agenticform.model.entity.WorkspaceRole;
import com.agenticform.repository.EmailVerificationTokenRepository;
import com.agenticform.repository.IntegrationConnectionRepository;
import com.agenticform.repository.PasswordResetTokenRepository;
import com.agenticform.repository.UserOAuthAccountRepository;
import com.agenticform.repository.UserRepository;
import com.agenticform.repository.WorkspaceMemberRepository;
import com.agenticform.repository.WorkspaceRepository;
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
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final WorkspaceRepository workspaceRepository;
    private final IntegrationConnectionRepository integrationConnectionRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final EmailVerificationTokenRepository emailVerificationTokenRepository;
    private final AdminBootstrapService adminBootstrapService;

    public AuthService(
            UserRepository userRepository,
            UserOAuthAccountRepository oauthAccountRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenProvider jwtTokenProvider,
            AuthenticationManager authenticationManager,
            OAuthCodeStore oauthCodeStore,
            EmailVerificationService emailVerificationService,
            WorkspaceService workspaceService,
            WorkspaceMemberRepository workspaceMemberRepository,
            WorkspaceRepository workspaceRepository,
            IntegrationConnectionRepository integrationConnectionRepository,
            PasswordResetTokenRepository passwordResetTokenRepository,
            EmailVerificationTokenRepository emailVerificationTokenRepository,
            AdminBootstrapService adminBootstrapService) {
        this.userRepository = userRepository;
        this.oauthAccountRepository = oauthAccountRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.authenticationManager = authenticationManager;
        this.oauthCodeStore = oauthCodeStore;
        this.emailVerificationService = emailVerificationService;
        this.workspaceService = workspaceService;
        this.workspaceMemberRepository = workspaceMemberRepository;
        this.workspaceRepository = workspaceRepository;
        this.integrationConnectionRepository = integrationConnectionRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.emailVerificationTokenRepository = emailVerificationTokenRepository;
        this.adminBootstrapService = adminBootstrapService;
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
        user.setRole(adminBootstrapService.roleForEmail(email));
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
        user.setRole(adminBootstrapService.roleForEmail(email));
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
        if (existing != null && (existing.isBlocked()
                || !existing.isPasswordEnabled()
                || !existing.isEmailVerified())) {
            throw new BadCredentialsException("Invalid credentials");
        }

        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(email, request.password()));

        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found"));

        if (user.isBlocked() || !user.isPasswordEnabled() || !user.isEmailVerified()) {
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

        if (user.isBlocked()) {
            throw new InvalidOAuthCodeException();
        }

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
    public UserProfileResponse updateProfile(UserPrincipal principal, UpdateProfileRequest request) {
        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found"));

        String first = request.firstName().trim().replaceAll("\\s+", " ");
        String last = request.lastName().trim().replaceAll("\\s+", " ");
        user.setFullName((first + " " + last).trim());

        User saved = userRepository.save(user);
        return toProfile(saved);
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

    /**
     * Change ou définit le mot de passe du compte connecté.
     * Invalide les anciennes sessions JWT et renvoie un nouvel access token.
     */
    @Transactional
    public AuthSessionResult changePassword(UserPrincipal principal, ChangePasswordRequest request) {
        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found"));

        String newPassword = request.newPassword();
        if (passwordEncoder.matches(newPassword, user.getPassword())) {
            throw new SamePasswordException();
        }

        if (user.isPasswordEnabled()) {
            String current = request.currentPassword();
            if (current == null || current.isBlank()
                    || !passwordEncoder.matches(current, user.getPassword())) {
                throw new InvalidCurrentPasswordException();
            }
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        user.setPasswordEnabled(true);
        user.setTokenVersion(user.getTokenVersion() + 1);
        User saved = userRepository.save(user);

        String token = jwtTokenProvider.generateToken(saved);
        return toAuthSession(token, saved);
    }

    /**
     * Supprime définitivement le compte et les espaces dont l'utilisateur est propriétaire.
     */
    @Transactional
    public void deleteAccount(UserPrincipal principal, String confirmEmail) {
        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found"));

        String expected = normalizeEmail(user.getEmail());
        String provided = normalizeEmail(confirmEmail);
        if (expected == null || provided == null || !expected.equals(provided)) {
            throw new AccountDeleteConfirmationException();
        }

        deleteUserAndOwnedWorkspaces(user);
    }

    /** Suppression admin ou auto-suppression de compte (espaces dont l'user est owner inclus). */
    @Transactional
    public void deleteUserAndOwnedWorkspaces(User user) {
        java.util.List<WorkspaceMember> memberships =
                new java.util.ArrayList<>(workspaceMemberRepository.findAllByUserId(user.getId()));

        java.util.List<Long> ownedWorkspaceIds = memberships.stream()
                .filter(m -> m.getRole() == WorkspaceRole.OWNER)
                .map(m -> m.getWorkspace().getId())
                .distinct()
                .toList();

        for (Long workspaceId : ownedWorkspaceIds) {
            workspaceMemberRepository.deleteAll(
                    workspaceMemberRepository.findAllByWorkspaceId(workspaceId));
            workspaceRepository.deleteById(workspaceId);
        }

        for (WorkspaceMember membership : workspaceMemberRepository.findAllByUserId(user.getId())) {
            workspaceMemberRepository.delete(membership);
        }

        integrationConnectionRepository.deleteByUserId(user.getId());
        passwordResetTokenRepository.deleteByUser(user);
        emailVerificationTokenRepository.deleteByUser(user);
        userRepository.delete(user);
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
