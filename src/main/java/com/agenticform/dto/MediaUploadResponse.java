package com.agenticform.dto;

/**
 * Réponse après upload d’un fichier média (URL same-origin stable).
 */
public record MediaUploadResponse(String url, String contentType, long sizeBytes) {
}
