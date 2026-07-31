package com.agenticform.service;

import java.time.LocalDateTime;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.agenticform.dto.ForgotPasswordRequest;
import com.agenticform.dto.ResetPasswordRequest;
import com.agenticform.exception.InvalidPasswordResetTokenException;
import com.agenticform.exception.SamePasswordException;
import com.agenticform.model.entity.PasswordResetToken;
import com.agenticform.model.entity.User;
import com.agenticform.repository.PasswordResetTokenRepository;
import com.agenticform.repository.UserRepository;
import com.agenticform.security.TokenHashUtils;

@Service
public class PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);
    private static final int TOKEN_VALIDITY_MINUTES = 30;

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final String resetPasswordBaseUrl;

    public PasswordResetService(
            UserRepository userRepository,
            PasswordResetTokenRepository tokenRepository,
            PasswordEncoder passwordEncoder,
            EmailService emailService,
            @Value("${app.reset-password.url:http://localhost:5173/reset-password}") String resetPasswordBaseUrl) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
        this.resetPasswordBaseUrl = resetPasswordBaseUrl.replaceAll("/$", "");
    }

    /**
     * Toujours neutre côté API (pas d'énumération d'e-mails).
     * Envoie un mail pour tout compte existant (local, OAuth ou hybride).
     */
    @Transactional
    public void requestPasswordReset(ForgotPasswordRequest request) {
        String email = normalizeEmail(request.email());
        userRepository.findByEmail(email).ifPresent(user -> {
            tokenRepository.deleteByUser(user);

            String rawToken = UUID.randomUUID().toString();
            PasswordResetToken token = new PasswordResetToken();
            token.setTokenHash(TokenHashUtils.sha256Hex(rawToken));
            token.setUser(user);
            token.setExpiryDate(LocalDateTime.now().plusMinutes(TOKEN_VALIDITY_MINUTES));
            tokenRepository.save(token);

            String resetLink = resetPasswordBaseUrl + "?token=" + rawToken;
            log.info("Issuing password reset token for userId={} email={}", user.getId(), email);
            try {
                emailService.sendPasswordResetEmail(user.getEmail(), resetLink);
            } catch (RuntimeException ex) {
                log.error("Password reset email could not be sent for {} — token was still persisted",
                        email, ex);
            }
        });
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        String tokenHash = TokenHashUtils.sha256Hex(request.token().trim());
        PasswordResetToken resetToken = tokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(InvalidPasswordResetTokenException::new);

        if (resetToken.isExpired()) {
            tokenRepository.delete(resetToken);
            throw new InvalidPasswordResetTokenException();
        }

        User user = resetToken.getUser();
        String currentHash = user.getPassword();
        if (currentHash != null && !currentHash.isBlank()
                && passwordEncoder.matches(request.newPassword(), currentHash)) {
            throw new SamePasswordException();
        }

        user.setPassword(passwordEncoder.encode(request.newPassword()));
        // Active le login email/mot de passe (comptes OAuth-only → hybrides).
        user.setPasswordEnabled(true);
        // H-2 : invalide tous les JWT encore en circulation.
        user.setTokenVersion(user.getTokenVersion() + 1);
        userRepository.save(user);

        tokenRepository.deleteByUser(user);
    }

    private String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }
}
