package com.agenticform.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Nœud de logique d’affichage (condition ou groupe AND/OR).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record VisibilityNodeDto(
        String id,
        String type,
        String join,
        String refKind,
        String refId,
        String operator,
        String value,
        List<VisibilityNodeDto> children
) {
}
