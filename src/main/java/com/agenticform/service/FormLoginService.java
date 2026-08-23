package com.agenticform.service;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.agenticform.dto.FormLoginPasswordVerifyRequest;
import com.agenticform.dto.FormLoginPasswordVerifyResponse;
import com.agenticform.dto.FormLoginSendCodeRequest;
import com.agenticform.dto.FormLoginVerifyRequest;
import com.agenticform.dto.FormLoginVerifyResponse;
import com.agenticform.dto.FormPageDto;
import com.agenticform.dto.LoginConfigDto;
import com.agenticform.dto.PagesDocumentDto;
import com.agenticform.exception.FormNotAvailableException;
import com.agenticform.model.entity.Form;
import com.agenticform.model.entity.FormStatus;

@Service
public class FormLoginService {

    private static final int CODE_TTL_MINUTES = 15;

    private final EmailService emailService;
    private final FormMapper formMapper;
    private final PasswordEncoder passwordEncoder;
    private final LoginConfigSupport loginConfigSupport;
    private final SecureRandom random = new SecureRandom();
    private final ConcurrentHashMap<String, PendingCode> pendingCodes = new ConcurrentHashMap<>();

    public FormLoginService(
            EmailService emailService,
            FormMapper formMapper,
            PasswordEncoder passwordEncoder,
            LoginConfigSupport loginConfigSupport) {
        this.emailService = emailService;
        this.formMapper = formMapper;
        this.passwordEncoder = passwordEncoder;
        this.loginConfigSupport = loginConfigSupport;
    }

    private record PendingCode(String code, Instant expiresAt) {
    }

    public void sendCode(Form form, FormLoginSendCodeRequest request) {
        requirePublished(form);
        LoginConfigDto config = loginConfig(form);
        if (loginConfigSupport.isPasswordMode(config)) {
            throw new IllegalArgumentException("Ce formulaire utilise la connexion par mot de passe.");
        }
        String email = request.email().trim().toLowerCase();
        assertDomainAllowed(email, config);
        String code = generateCode();
        pendingCodes.put(key(form.getId(), email), new PendingCode(code, Instant.now().plus(CODE_TTL_MINUTES, ChronoUnit.MINUTES)));
        String subject = config != null && StringUtils.hasText(config.emailSubject())
                ? config.emailSubject().trim()
                : "Votre code de sécurité";
        emailService.sendFormLoginCodeEmail(email, code, subject, form.getTitle());
    }

    public FormLoginVerifyResponse verify(Form form, FormLoginVerifyRequest request) {
        requirePublished(form);
        String email = request.email().trim().toLowerCase();
        String submitted = request.code().trim();
        PendingCode pending = pendingCodes.get(key(form.getId(), email));
        if (pending == null || Instant.now().isAfter(pending.expiresAt())) {
            pendingCodes.remove(key(form.getId(), email));
            throw new IllegalArgumentException("Code invalide ou expiré.");
        }
        if (!pending.code().equals(submitted)) {
            throw new IllegalArgumentException("Code invalide ou expiré.");
        }
        pendingCodes.remove(key(form.getId(), email));
        return new FormLoginVerifyResponse(true, email);
    }

    public FormLoginPasswordVerifyResponse verifyPassword(Form form, FormLoginPasswordVerifyRequest request) {
        requirePublished(form);
        LoginConfigDto config = loginConfig(form);
        if (!loginConfigSupport.isPasswordMode(config)) {
            throw new IllegalArgumentException("Ce formulaire n'utilise pas la connexion par mot de passe.");
        }
        if (!loginConfigSupport.isPasswordConfigured(config)) {
            throw new IllegalArgumentException("Mot de passe non configuré pour ce formulaire.");
        }
        String submitted = request.password();
        if (!StringUtils.hasText(submitted)) {
            throw new IllegalArgumentException("Indiquez le mot de passe.");
        }
        if (!passwordEncoder.matches(submitted, config.passwordHash())) {
            throw new IllegalArgumentException(
                    "Mot de passe incorrect. Réessayez ou contactez le propriétaire du formulaire.");
        }
        return new FormLoginPasswordVerifyResponse(true);
    }

    public void setLoginPassword(Form form, String plainPassword) {
        if (!StringUtils.hasText(plainPassword)) {
            throw new IllegalArgumentException("Indiquez un mot de passe.");
        }
        LoginConfigDto config = loginConfigFromPagesJson(form.getPagesJson());
        if (config == null) {
            throw new IllegalArgumentException("Ce formulaire n'a pas de page Connexion.");
        }
        loginConfigSupport.applyPasswordHash(form, formMapper, passwordEncoder.encode(plainPassword.trim()));
    }

    public void requireGoogleLoginAllowed(Form form) {
        requirePublished(form);
        LoginConfigDto config = loginConfig(form);
        if (loginConfigSupport.isPasswordMode(config)) {
            throw new IllegalArgumentException("Ce formulaire n'utilise pas la connexion Google.");
        }
        if (!loginConfigSupport.isGoogleMethodEnabled(config)) {
            throw new IllegalArgumentException("La connexion Google n'est pas activée pour ce formulaire.");
        }
    }

    public FormLoginVerifyResponse verifyGoogleEmail(Form form, String email) {
        requireGoogleLoginAllowed(form);
        LoginConfigDto config = loginConfig(form);
        String normalized = email.trim().toLowerCase();
        if (!normalized.contains("@")) {
            throw new IllegalArgumentException("Adresse e-mail Google invalide.");
        }
        assertDomainAllowed(normalized, config);
        return new FormLoginVerifyResponse(true, normalized);
    }

    private void requirePublished(Form form) {
        if (form.getStatus() != FormStatus.PUBLISHED) {
            throw new FormNotAvailableException(form.getId());
        }
    }

    private String key(Long formId, String email) {
        return formId + ":" + email;
    }

    private String generateCode() {
        int value = random.nextInt(1_000_000);
        return String.format("%06d", value);
    }

    /** Config login pour le lien public (snapshot publié si présent). */
    private LoginConfigDto loginConfig(Form form) {
        return loginConfigFromPagesJson(publishedPagesJson(form));
    }

    private String publishedPagesJson(Form form) {
        var snapshot = formMapper.parsePublishedSnapshot(form.getPublishedSnapshotJson());
        if (snapshot != null) {
            return formMapper.serializePagesDocument(snapshot.pages(), snapshot.progressBar());
        }
        return form.getPagesJson();
    }

    private LoginConfigDto loginConfigFromPagesJson(String pagesJson) {
        PagesDocumentDto document = formMapper.parsePagesDocument(pagesJson);
        if (document == null || document.pages() == null) {
            return null;
        }
        for (FormPageDto page : document.pages()) {
            if (page != null && "login".equalsIgnoreCase(page.type())) {
                return page.loginConfig();
            }
        }
        return null;
    }

    private void assertDomainAllowed(String email, LoginConfigDto config) {
        if (config == null || config.restrictDomains() == null || !config.restrictDomains()) {
            return;
        }
        List<String> allowed = config.allowedDomains();
        if (allowed == null || allowed.isEmpty()) {
            return;
        }
        String domain = email.contains("@") ? email.substring(email.indexOf('@') + 1) : "";
        boolean ok = allowed.stream()
                .filter(StringUtils::hasText)
                .map(value -> value.trim().toLowerCase().replace("@", ""))
                .anyMatch(allowedDomain -> domain.equals(allowedDomain) || domain.endsWith("." + allowedDomain));
        if (!ok) {
            throw new IllegalArgumentException("Ce domaine e-mail n'est pas autorisé.");
        }
    }
}
