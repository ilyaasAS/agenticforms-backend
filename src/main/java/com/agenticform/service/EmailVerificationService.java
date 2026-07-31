package com.agenticform.service;

import java.time.LocalDateTime;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.agenticform.dto.ResendVerificationRequest;
import com.agenticform.exception.InvalidEmailVerificationTokenException;
import com.agenticform.model.entity.EmailVerificationToken;
import com.agenticform.model.entity.User;
import com.agenticform.repository.EmailVerificationTokenRepository;
import com.agenticform.repository.UserRepository;
import com.agenticform.security.TokenHashUtils;

@Service
public class EmailVerificationService {

    private static final Logger log = LoggerFactory.getLogger(EmailVerificationService.class);
    private static final int TOKEN_VALIDITY_HOURS = 24;

    private final UserRepository userRepository;
    private final EmailVerificationTokenRepository tokenRepository;
    private final EmailService emailService;
    private final String verifyEmailBaseUrl;

    public EmailVerificationService(
            UserRepository userRepository,
            EmailVerificationTokenRepository tokenRepository,
            EmailService emailService,
            @Value("${app.verify-email.url:http://localhost:5173/verify-email}") String verifyEmailBaseUrl) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.emailService = emailService;
        this.verifyEmailBaseUrl = verifyEmailBaseUrl.replaceAll("/$", "");
    }

    @Transactional
    public void issueAndSend(User user) {
        tokenRepository.deleteByUser(user);

        String rawToken = UUID.randomUUID().toString();
        EmailVerificationToken token = new EmailVerificationToken();
        token.setUser(user);
        token.setTokenHash(TokenHashUtils.sha256Hex(rawToken));
        token.setExpiresAt(LocalDateTime.now().plusHours(TOKEN_VALIDITY_HOURS));
        tokenRepository.save(token);

        String link = verifyEmailBaseUrl + "?token=" + rawToken;
        log.info("Issuing email verification token for userId={} email={}", user.getId(), user.getEmail());
        try {
            emailService.sendEmailVerificationEmail(user.getEmail(), link);
        } catch (RuntimeException ex) {
            log.error("Verification email could not be sent for {} — token was still persisted",
                    user.getEmail(), ex);
        }
    }

    /**
     * Réponse API toujours neutre (anti-énumération).
     */
    @Transactional
    public void resend(ResendVerificationRequest request) {
        String email = normalizeEmail(request.email());
        userRepository.findByEmail(email).ifPresent(user -> {
            if (user.isEmailVerified() || !user.isPasswordEnabled()) {
                return;
            }
            issueAndSend(user);
        });
    }

    @Transactional
    public void verify(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new InvalidEmailVerificationTokenException();
        }
        String tokenHash = TokenHashUtils.sha256Hex(rawToken.trim());
        EmailVerificationToken token = tokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(InvalidEmailVerificationTokenException::new);

        if (token.isUsed() || token.isExpired()) {
            tokenRepository.delete(token);
            throw new InvalidEmailVerificationTokenException();
        }

        User user = token.getUser();
        user.markEmailVerified();
        userRepository.save(user);

        token.setUsedAt(LocalDateTime.now());
        tokenRepository.save(token);
        tokenRepository.deleteByUser(user);
    }

    private String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }
}
