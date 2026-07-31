package com.agenticform.dto;

public record ResolveImageResponse(
        boolean ok,
        String imageUrl,
        String source,
        String message
) {
    public static ResolveImageResponse success(String imageUrl, String source) {
        return new ResolveImageResponse(true, imageUrl, source, null);
    }

    public static ResolveImageResponse failure(String message) {
        return new ResolveImageResponse(false, null, "none", message);
    }
}
