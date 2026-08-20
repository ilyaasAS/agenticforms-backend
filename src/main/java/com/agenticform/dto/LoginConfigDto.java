package com.agenticform.dto;

import java.util.List;

/**
 * Configuration de la page Connexion (persistée dans pages_json).
 */
public record LoginConfigDto(
        String verificationType,
        List<String> methods,
        Boolean allowEditResponses,
        String buttonText,
        Boolean restrictDomains,
        List<String> allowedDomains,
        Boolean singleSubmissionLimit,
        String limitTitle,
        String limitSubtitle,
        String emailSubject,
        String title,
        String description,
        /** Hash BCrypt — stocké en base, jamais exposé au client public. */
        String passwordHash,
        /** Indicatif API (calculé à la lecture, non persisté). */
        Boolean passwordConfigured) {
}
