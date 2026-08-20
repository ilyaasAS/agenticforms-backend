package com.agenticform.dto;

import java.util.List;

/**
 * Contenu de pages_json : liste de pages + barre de progression optionnelle.
 */
public record PagesDocumentDto(
        List<FormPageDto> pages,
        ProgressBarConfigDto progressBar
) {
}
