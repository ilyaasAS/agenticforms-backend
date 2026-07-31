package com.agenticform.service;

import java.text.Normalizer;

import org.springframework.stereotype.Service;

import com.agenticform.repository.WorkspaceRepository;

@Service
public class SlugService {

    private static final int MAX_SLUG_LENGTH = 255;
    private static final String FALLBACK_SLUG = "workspace";

    private final WorkspaceRepository workspaceRepository;

    public SlugService(WorkspaceRepository workspaceRepository) {
        this.workspaceRepository = workspaceRepository;
    }

    /**
     * Convertit un libellé en slug kebab-case (ASCII, sans accents).
     */
    public String toKebabCase(String input) {
        if (input == null || input.isBlank()) {
            return FALLBACK_SLUG;
        }

        String normalized = Normalizer.normalize(input.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        String slug = normalized.toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");

        if (slug.isBlank()) {
            return FALLBACK_SLUG;
        }
        return truncate(slug, MAX_SLUG_LENGTH);
    }

    /**
     * Génère un slug unique en ajoutant {@code -2}, {@code -3}, … si nécessaire.
     */
    public String generateUniqueSlug(String baseName) {
        String base = toKebabCase(baseName);
        if (!workspaceRepository.existsBySlug(base)) {
            return base;
        }

        int suffix = 2;
        while (suffix <= 10_000) {
            String suffixPart = "-" + suffix;
            String candidate = truncate(base, MAX_SLUG_LENGTH - suffixPart.length()) + suffixPart;
            if (!workspaceRepository.existsBySlug(candidate)) {
                return candidate;
            }
            suffix++;
        }
        throw new IllegalStateException("Unable to generate unique workspace slug");
    }

    private static String truncate(String value, int maxLength) {
        if (maxLength <= 0) {
            return FALLBACK_SLUG;
        }
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
