package com.agenticform.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.agenticform.dto.FormPageDto;
import com.agenticform.dto.LoginConfigDto;
import com.agenticform.model.entity.Form;

@Component
public class LoginConfigSupport {

    public boolean isPasswordMode(LoginConfigDto config) {
        return config != null && "password".equalsIgnoreCase(config.verificationType());
    }

    public boolean isPasswordConfigured(LoginConfigDto config) {
        return config != null && StringUtils.hasText(config.passwordHash());
    }

    public boolean isGoogleMethodEnabled(LoginConfigDto config) {
        if (config == null || isPasswordMode(config)) {
            return false;
        }
        if (config.methods() == null) {
            return false;
        }
        return config.methods().stream().anyMatch(method -> "google".equalsIgnoreCase(method));
    }

    public LoginConfigDto sanitizeForClient(LoginConfigDto config) {
        if (config == null) {
            return null;
        }
        boolean configured = StringUtils.hasText(config.passwordHash());
        return new LoginConfigDto(
                config.verificationType(),
                config.methods(),
                config.allowEditResponses(),
                config.buttonText(),
                config.restrictDomains(),
                config.allowedDomains(),
                config.singleSubmissionLimit(),
                config.limitTitle(),
                config.limitSubtitle(),
                config.emailSubject(),
                config.title(),
                config.description(),
                null,
                configured);
    }

    public List<FormPageDto> sanitizePagesForClient(List<FormPageDto> pages) {
        if (pages == null || pages.isEmpty()) {
            return pages == null ? List.of() : pages;
        }
        return pages.stream().map(this::sanitizePageForClient).toList();
    }

    public FormPageDto sanitizePageForClient(FormPageDto page) {
        if (page == null || page.loginConfig() == null) {
            return page;
        }
        return copyPage(page, sanitizeForClient(page.loginConfig()));
    }

    public List<FormPageDto> mergeLoginPasswordHashes(List<FormPageDto> existing, List<FormPageDto> incoming) {
        if (incoming == null) {
            return incoming;
        }
        Map<String, String> hashesByPageId = new HashMap<>();
        if (existing != null) {
            for (FormPageDto page : existing) {
                if (page == null || !"login".equalsIgnoreCase(page.type()) || page.loginConfig() == null) {
                    continue;
                }
                if (StringUtils.hasText(page.loginConfig().passwordHash())) {
                    hashesByPageId.put(page.id(), page.loginConfig().passwordHash());
                }
            }
        }
        List<FormPageDto> merged = new ArrayList<>();
        for (FormPageDto page : incoming) {
            if (page == null) {
                continue;
            }
            if (!"login".equalsIgnoreCase(page.type()) || page.loginConfig() == null) {
                merged.add(page);
                continue;
            }
            LoginConfigDto incomingConfig = page.loginConfig();
            String preserved = hashesByPageId.get(page.id());
            String hash = StringUtils.hasText(incomingConfig.passwordHash())
                    ? incomingConfig.passwordHash()
                    : preserved;
            LoginConfigDto mergedConfig = copyLoginConfig(incomingConfig, hash);
            merged.add(copyPage(page, mergedConfig));
        }
        return merged;
    }

    public void applyPasswordHash(Form form, FormMapper formMapper, String passwordHash) {
        var document = formMapper.parsePagesDocument(form.getPagesJson());
        List<FormPageDto> pages = document.pages() == null ? List.of() : document.pages();
        List<FormPageDto> nextPages = pages.stream()
                .map(page -> {
                    if (page == null || !"login".equalsIgnoreCase(page.type()) || page.loginConfig() == null) {
                        return page;
                    }
                    LoginConfigDto config = copyLoginConfig(page.loginConfig(), passwordHash);
                    return copyPage(page, config);
                })
                .toList();
        form.setPagesJson(formMapper.serializePagesDocument(nextPages, document.progressBar()));
    }

    private LoginConfigDto copyLoginConfig(LoginConfigDto source, String passwordHash) {
        return new LoginConfigDto(
                source.verificationType(),
                source.methods(),
                source.allowEditResponses(),
                source.buttonText(),
                source.restrictDomains(),
                source.allowedDomains(),
                source.singleSubmissionLimit(),
                source.limitTitle(),
                source.limitSubtitle(),
                source.emailSubject(),
                source.title(),
                source.description(),
                passwordHash,
                null);
    }

    private FormPageDto copyPage(FormPageDto page, LoginConfigDto loginConfig) {
        return new FormPageDto(
                page.id(),
                page.type(),
                page.title(),
                page.navLabel(),
                page.description(),
                page.fieldIds(),
                page.buttonText(),
                page.headerImage(),
                page.coverLayoutMedia(),
                page.coverImagePosition(),
                page.customCoverLayout(),
                page.endingConfig(),
                page.reviewConfig(),
                loginConfig,
                page.paymentConfig(),
                page.contentBlocks(),
                page.canvasOrder(),
                page.progressStepId());
    }
}
