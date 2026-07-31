package com.agenticform.dto;

public record ProxiedImage(
        byte[] bytes,
        String contentType
) {
}
