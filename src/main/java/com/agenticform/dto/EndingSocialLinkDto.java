package com.agenticform.dto;

/**
 * Lien réseau social sur un bloc ending {@code social}.
 */
public record EndingSocialLinkDto(
        String id,
        String platform,
        String url
) {
}
