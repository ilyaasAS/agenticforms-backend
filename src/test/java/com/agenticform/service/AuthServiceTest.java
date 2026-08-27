package com.agenticform.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.agenticform.dto.AuthResponse;
import com.agenticform.dto.AuthSessionResult;
import com.agenticform.dto.ChangePasswordRequest;
import com.agenticform.dto.LocalLoginRequest;
import com.agenticform.dto.LoginRequest;
import com.agenticform.dto.RegisterRequest;
import com.agenticform.dto.SignupRequest;
import com.agenticform.dto.SignupResponse;
import com.agenticform.dto.UpdateProfileRequest;
import com.agenticform.exception.AccountDeleteConfirmationException;
import com.agenticform.exception.InvalidOAuthCodeException;
import com.agenticform.exception.SamePasswordException;
import com.agenticform.model.entity.Role;
import com.agenticform.model.entity.User;
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

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private UserOAuthAccountRepository oauthAccountRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtTokenProvider jwtTokenProvider;
    @Mock
    private AuthenticationManager authenticationManager;
    @Mock
    private OAuthCodeStore oauthCodeStore;
    @Mock
    private EmailVerificationService emailVerificationService;
    @Mock
    private WorkspaceService workspaceService;
    @Mock
    private WorkspaceMemberRepository workspaceMemberRepository;
    @Mock
    private WorkspaceRepository workspaceRepository;
    @Mock
    private IntegrationConnectionRepository integrationConnectionRepository;
    @Mock
    private PasswordResetTokenRepository passwordResetTokenRepository;
    @Mock
    private EmailVerificationTokenRepository emailVerificationTokenRepository;
    @Mock
    private AdminBootstrapService adminBootstrapService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                userRepository,
                oauthAccountRepository,
                passwordEncoder,
                jwtTokenProvider,
                authenticationManager,
                oauthCodeStore,
                emailVerificationService,
                workspaceService,
                workspaceMemberRepository,
                workspaceRepository,
                integrationConnectionRepository,
                passwordResetTokenRepository,
                emailVerificationTokenRepository,
                adminBootstrapService,
                604_800_000L);
    }

    @Test
    void signupCreatesUserHashesPasswordAndSendsVerification() {
        given(userRepository.existsByEmail("ada@example.com")).willReturn(false);
        given(passwordEncoder.encode("StrongP@ssword16!")).willReturn("bcrypt-hash");
        given(adminBootstrapService.roleForEmail("ada@example.com")).willReturn(Role.ROLE_USER);
        given(userRepository.save(any(User.class))).willAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(42L);
            return u;
        });

        SignupResponse response = authService.signup(
                new SignupRequest("Ada@Example.com", "StrongP@ssword16!", "Ada"));

        assertTrue(response.message().contains("confirmation"));
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertEquals("ada@example.com", captor.getValue().getEmail());
        assertEquals("bcrypt-hash", captor.getValue().getPassword());
        assertEquals(false, captor.getValue().isEmailVerified());
        verify(workspaceService).createDefaultWorkspaceForUser(any(User.class));
        verify(emailVerificationService).issueAndSend(any(User.class));
    }

    @Test
    void signupExistingEmailIsNeutralAndStillHashes() {
        given(userRepository.existsByEmail("ada@example.com")).willReturn(true);
        given(passwordEncoder.encode("StrongP@ssword16!")).willReturn("bcrypt-hash");

        SignupResponse response = authService.signup(
                new SignupRequest("ada@example.com", "StrongP@ssword16!", "Ada"));

        assertTrue(response.message().contains("confirmation"));
        verify(userRepository, never()).save(any());
        verify(passwordEncoder).encode("StrongP@ssword16!");
    }

    /**
     * Preuve anti-injection : un mot de passe contenant {@code --} (commentaire MySQL)
     * est traité comme une chaîne littérale (hachage BCrypt), pas comme du SQL.
     * Avec JPA / requêtes préparées, la suite de la requête n'est pas « commentée ».
     */
    @Test
    void signupPasswordWithSqlCommentCharsIsHashedLiterally() {
        String passwordWithSqlComment = "ValidPass16!--drop";
        given(userRepository.existsByEmail("bob@example.com")).willReturn(false);
        given(passwordEncoder.encode(passwordWithSqlComment)).willReturn("bcrypt-of-literal");
        given(adminBootstrapService.roleForEmail("bob@example.com")).willReturn(Role.ROLE_USER);
        given(userRepository.save(any(User.class))).willAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(7L);
            return u;
        });

        authService.signup(new SignupRequest("bob@example.com", passwordWithSqlComment, "Bob"));

        verify(passwordEncoder).encode(eq(passwordWithSqlComment));
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertEquals("bcrypt-of-literal", captor.getValue().getPassword());
    }

    @Test
    void loginLocalRejectsUnverifiedEmail() {
        User user = new User();
        user.setId(1L);
        user.setEmail("ada@example.com");
        user.setPasswordEnabled(true);
        user.setEmailVerified(false);
        given(userRepository.findByEmail("ada@example.com")).willReturn(Optional.of(user));

        assertThrows(BadCredentialsException.class,
                () -> authService.loginLocal(
                        new LocalLoginRequest("ada@example.com", "StrongP@ssword16!", false)));
        verify(authenticationManager, never()).authenticate(any());
    }

    @Test
    void loginLocalReturnsJwtSessionWhenCredentialsOk() {
        User user = new User();
        user.setId(10L);
        user.setEmail("ada@example.com");
        user.setFullName("Ada");
        user.setRole(Role.ROLE_USER);
        user.setPasswordEnabled(true);
        user.setEmailVerified(true);

        UserPrincipal principal = org.mockito.Mockito.mock(UserPrincipal.class);
        given(principal.getId()).willReturn(10L);
        Authentication authentication = new UsernamePasswordAuthenticationToken(principal, null);

        given(userRepository.findByEmail("ada@example.com")).willReturn(Optional.of(user));
        given(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .willReturn(authentication);
        given(userRepository.findById(10L)).willReturn(Optional.of(user));
        given(jwtTokenProvider.getExpirationMs()).willReturn(3_600_000L);
        given(jwtTokenProvider.generateToken(eq(user), anyLong())).willReturn("jwt-token");

        AuthSessionResult session = authService.loginLocal(
                new LocalLoginRequest("ada@example.com", "StrongP@ssword16!", false));

        assertEquals("jwt-token", session.accessToken());
        assertEquals(3_600_000L, session.expiresInMs());
        assertEquals("ada@example.com", session.response().user().email());
        verify(authenticationManager).authenticate(any());
        verify(jwtTokenProvider).generateToken(user, 3_600_000L);
    }

    @Test
    void registerCreatesUnverifiedUser() {
        given(userRepository.existsByEmail("new@example.com")).willReturn(false);
        given(passwordEncoder.encode("StrongP@ssword16!")).willReturn("hash");
        given(adminBootstrapService.roleForEmail("new@example.com")).willReturn(Role.ROLE_USER);
        given(userRepository.save(any(User.class))).willAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(3L);
            return u;
        });

        authService.register(new RegisterRequest("new@example.com", "StrongP@ssword16!"));

        verify(emailVerificationService).issueAndSend(any(User.class));
        verify(workspaceService).createDefaultWorkspaceForUser(any(User.class));
    }

    @Test
    void registerExistingEmailOnlyHashes() {
        given(userRepository.existsByEmail("old@example.com")).willReturn(true);
        given(passwordEncoder.encode("StrongP@ssword16!")).willReturn("hash");

        authService.register(new RegisterRequest("old@example.com", "StrongP@ssword16!"));

        verify(userRepository, never()).save(any());
        verify(passwordEncoder).encode("StrongP@ssword16!");
    }

    @Test
    void loginRejectsBlockedUser() {
        User user = verifiedUser(1L, "ada@example.com");
        user.setBlocked(true);
        given(userRepository.findByEmail("ada@example.com")).willReturn(Optional.of(user));

        assertThrows(BadCredentialsException.class,
                () -> authService.login(new LoginRequest("ada@example.com", "StrongP@ssword16!", false)));
    }

    @Test
    void loginRememberMeUsesLongTtl() {
        User user = verifiedUser(10L, "ada@example.com");
        UserPrincipal principal = org.mockito.Mockito.mock(UserPrincipal.class);
        given(principal.getId()).willReturn(10L);
        Authentication authentication = new UsernamePasswordAuthenticationToken(principal, null);

        given(userRepository.findByEmail("ada@example.com")).willReturn(Optional.of(user));
        given(authenticationManager.authenticate(any())).willReturn(authentication);
        given(userRepository.findById(10L)).willReturn(Optional.of(user));
        given(jwtTokenProvider.generateToken(eq(user), eq(604_800_000L))).willReturn("jwt-remember");

        AuthSessionResult session = authService.login(
                new LoginRequest("ada@example.com", "StrongP@ssword16!", true));

        assertEquals(604_800_000L, session.expiresInMs());
        assertEquals("jwt-remember", session.accessToken());
    }

    @Test
    void exchangeOAuthCodeReturnsSession() {
        User user = verifiedUser(5L, "oauth@example.com");
        given(oauthCodeStore.consume("code-1")).willReturn(Optional.of("jwt-oauth"));
        given(jwtTokenProvider.validateToken("jwt-oauth")).willReturn(true);
        given(jwtTokenProvider.getUserIdFromToken("jwt-oauth")).willReturn(5L);
        given(userRepository.findById(5L)).willReturn(Optional.of(user));
        given(jwtTokenProvider.getTokenVersionFromToken("jwt-oauth")).willReturn(0);
        given(jwtTokenProvider.getExpirationMs()).willReturn(3_600_000L);

        AuthSessionResult session = authService.exchangeOAuthCode("code-1");

        assertEquals("jwt-oauth", session.accessToken());
        assertEquals("oauth@example.com", session.response().user().email());
    }

    @Test
    void exchangeOAuthCodeRejectsUnknownCode() {
        given(oauthCodeStore.consume("bad")).willReturn(Optional.empty());
        assertThrows(InvalidOAuthCodeException.class, () -> authService.exchangeOAuthCode("bad"));
    }

    @Test
    void revokeSessionsIncrementsTokenVersion() {
        User user = verifiedUser(2L, "ada@example.com");
        user.setTokenVersion(4);
        given(userRepository.findById(2L)).willReturn(Optional.of(user));

        authService.revokeSessions(2L);

        assertEquals(5, user.getTokenVersion());
        verify(userRepository).save(user);
    }

    @Test
    void revokeSessionsNullIsNoOp() {
        authService.revokeSessions(null);
        verify(userRepository, never()).findById(any());
    }

    @Test
    void meReturnsProfile() {
        User user = verifiedUser(10L, "ada@example.com");
        user.setFullName("Ada Lovelace");
        given(userRepository.findById(10L)).willReturn(Optional.of(user));
        UserPrincipal principal = org.mockito.Mockito.mock(UserPrincipal.class);
        given(principal.getId()).willReturn(10L);

        var profile = authService.me(principal);

        assertEquals("ada@example.com", profile.email());
        assertEquals("Ada Lovelace", profile.fullName());
        assertEquals("ROLE_USER", profile.role());
    }

    @Test
    void updateProfileJoinsFirstAndLastName() {
        User user = verifiedUser(10L, "ada@example.com");
        given(userRepository.findById(10L)).willReturn(Optional.of(user));
        given(userRepository.save(user)).willReturn(user);
        UserPrincipal principal = org.mockito.Mockito.mock(UserPrincipal.class);
        given(principal.getId()).willReturn(10L);

        var profile = authService.updateProfile(principal, new UpdateProfileRequest("  Ada ", " Lovelace "));

        assertEquals("Ada Lovelace", user.getFullName());
        assertEquals("Ada Lovelace", profile.fullName());
    }

    @Test
    void changePasswordRejectsSamePassword() {
        User user = verifiedUser(10L, "ada@example.com");
        user.setPassword("hash");
        given(userRepository.findById(10L)).willReturn(Optional.of(user));
        given(passwordEncoder.matches("StrongP@ssword16!", "hash")).willReturn(true);
        UserPrincipal principal = org.mockito.Mockito.mock(UserPrincipal.class);
        given(principal.getId()).willReturn(10L);

        assertThrows(SamePasswordException.class,
                () -> authService.changePassword(
                        principal, new ChangePasswordRequest("old", "StrongP@ssword16!")));
    }

    @Test
    void changePasswordUpdatesHashAndReturnsNewJwt() {
        User user = verifiedUser(10L, "ada@example.com");
        user.setPassword("old-hash");
        user.setTokenVersion(1);
        given(userRepository.findById(10L)).willReturn(Optional.of(user));
        given(passwordEncoder.matches("NewStrongP@ss16!", "old-hash")).willReturn(false);
        given(passwordEncoder.matches("OldStrongP@ss16!", "old-hash")).willReturn(true);
        given(passwordEncoder.encode("NewStrongP@ss16!")).willReturn("new-hash");
        given(userRepository.save(user)).willReturn(user);
        given(jwtTokenProvider.generateToken(user)).willReturn("new-jwt");
        given(jwtTokenProvider.getExpirationMs()).willReturn(3_600_000L);
        UserPrincipal principal = org.mockito.Mockito.mock(UserPrincipal.class);
        given(principal.getId()).willReturn(10L);

        AuthSessionResult session = authService.changePassword(
                principal, new ChangePasswordRequest("OldStrongP@ss16!", "NewStrongP@ss16!"));

        assertEquals("new-hash", user.getPassword());
        assertEquals(2, user.getTokenVersion());
        assertEquals("new-jwt", session.accessToken());
    }

    @Test
    void deleteAccountRejectsWrongConfirmationEmail() {
        User user = verifiedUser(10L, "ada@example.com");
        given(userRepository.findById(10L)).willReturn(Optional.of(user));
        UserPrincipal principal = org.mockito.Mockito.mock(UserPrincipal.class);
        given(principal.getId()).willReturn(10L);

        assertThrows(AccountDeleteConfirmationException.class,
                () -> authService.deleteAccount(principal, "other@example.com"));
    }

    @Test
    void toLocalLoginResponseMapsUser() {
        User user = verifiedUser(1L, "a@b.com");
        AuthSessionResult session = new AuthSessionResult(
                "t",
                new AuthResponse(new AuthResponse.UserInfo(1L, "a@b.com", "ROLE_USER", "A")),
                1000L);

        assertEquals("a@b.com", authService.toLocalLoginResponse(session).user().email());
        assertEquals("a@b.com", authService.toOAuth2LoginResponse(session).user().email());
    }

    private static User verifiedUser(Long id, String email) {
        User user = new User();
        user.setId(id);
        user.setEmail(email);
        user.setRole(Role.ROLE_USER);
        user.setPasswordEnabled(true);
        user.setEmailVerified(true);
        user.setTokenVersion(0);
        return user;
    }
}
