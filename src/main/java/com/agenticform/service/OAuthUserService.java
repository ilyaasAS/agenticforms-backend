package com.agenticform.service;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.agenticform.exception.OAuthEmailNotVerifiedException;
import com.agenticform.exception.OAuthIdentityConflictException;
import com.agenticform.exception.OAuthLinkRequiresVerifiedEmailException;
import com.agenticform.model.entity.AuthProvider;
import com.agenticform.model.entity.Role;
import com.agenticform.model.entity.User;
import com.agenticform.model.entity.UserOAuthAccount;
import com.agenticform.repository.UserOAuthAccountRepository;
import com.agenticform.repository.UserRepository;

@Service
public class OAuthUserService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final UserOAuthAccountRepository oauthAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final WorkspaceService workspaceService;

    public OAuthUserService(
            UserRepository userRepository,
            UserOAuthAccountRepository oauthAccountRepository,
            PasswordEncoder passwordEncoder,
            WorkspaceService workspaceService) {
        this.userRepository = userRepository;
        this.oauthAccountRepository = oauthAccountRepository;
        this.passwordEncoder = passwordEncoder;
        this.workspaceService = workspaceService;
    }

    @Transactional(readOnly = true)
    public Optional<User> findByEmail(String email) {
        String normalizedEmail = normalizeEmail(email);
        if (normalizedEmail == null) {
            return Optional.empty();
        }
        return userRepository.findByEmail(normalizedEmail);
    }

    /**
     * Multi-linking : un User peut avoir Google + Azure (+ LOCAL password).
     * Auto-link par e-mail uniquement si le compte local a {@code emailVerified=true} (E-1).
     */
    @Transactional
    public OAuthUserResult findOrCreateOAuthUser(
            String email,
            String fullName,
            AuthProvider provider,
            String providerSubject,
            boolean emailVerified) {
        if (!emailVerified) {
            throw new OAuthEmailNotVerifiedException();
        }
        if (provider == null || provider == AuthProvider.LOCAL) {
            throw new IllegalArgumentException("OAuth provider required");
        }
        if (providerSubject == null || providerSubject.isBlank()) {
            throw new IllegalArgumentException("OAuth subject required");
        }

        String normalizedEmail = normalizeEmail(email);
        String providerKey = provider.name();
        String subject = providerSubject.trim();

        Optional<UserOAuthAccount> bySubject =
                oauthAccountRepository.findByProviderAndProviderSubject(providerKey, subject);
        if (bySubject.isPresent()) {
            User user = bySubject.get().getUser();
            markVerifiedFromIdp(user);
            return new OAuthUserResult(touchProfile(user, fullName), false);
        }

        return userRepository.findByEmail(normalizedEmail)
                .map(existing -> new OAuthUserResult(
                        linkProvider(existing, fullName, providerKey, subject), false))
                .orElseGet(() -> new OAuthUserResult(
                        createUserWithOAuth(normalizedEmail, fullName, providerKey, subject),
                        true));
    }

    /**
     * Rattache un provider OAuth à un User existant uniquement si e-mail déjà vérifié.
     */
    private User linkProvider(User existing, String fullName, String providerKey, String subject) {
        if (!existing.isEmailVerified()) {
            throw new OAuthLinkRequiresVerifiedEmailException();
        }

        Optional<UserOAuthAccount> existingLink =
                oauthAccountRepository.findByUserIdAndProvider(existing.getId(), providerKey);

        if (existingLink.isPresent()) {
            UserOAuthAccount link = existingLink.get();
            if (!subject.equals(link.getProviderSubject())) {
                throw new OAuthIdentityConflictException();
            }
            return touchProfile(existing, fullName);
        }

        if (oauthAccountRepository.findByProviderAndProviderSubject(providerKey, subject).isPresent()) {
            throw new OAuthIdentityConflictException();
        }

        UserOAuthAccount account = new UserOAuthAccount();
        account.setProvider(providerKey);
        account.setProviderSubject(subject);
        existing.addOAuthAccount(account);

        if (fullName != null && !fullName.isBlank()
                && (existing.getFullName() == null || existing.getFullName().isBlank())) {
            existing.setFullName(fullName);
        }

        return userRepository.save(existing);
    }

    private User createUserWithOAuth(
            String email,
            String fullName,
            String providerKey,
            String subject) {
        User user = new User();
        user.setEmail(email);
        user.setFullName(fullName);
        user.setPasswordEnabled(false);
        user.setRole(Role.ROLE_USER);
        user.setPassword(passwordEncoder.encode(randomSecret()));
        user.markEmailVerified();

        UserOAuthAccount account = new UserOAuthAccount();
        account.setProvider(providerKey);
        account.setProviderSubject(subject);
        user.addOAuthAccount(account);

        User saved = userRepository.save(user);
        workspaceService.createDefaultWorkspaceForUser(saved);
        return saved;
    }

    private void markVerifiedFromIdp(User user) {
        if (!user.isEmailVerified()) {
            user.markEmailVerified();
            userRepository.save(user);
        }
    }

    private User touchProfile(User existing, String fullName) {
        if (fullName != null && !fullName.isBlank()
                && (existing.getFullName() == null || existing.getFullName().isBlank())) {
            existing.setFullName(fullName);
            return userRepository.save(existing);
        }
        return existing;
    }

    private String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }

    private String randomSecret() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
