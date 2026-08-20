package com.agenticform.dto;

public record StripeStatusResponse(
        boolean configured,
        boolean connected,
        boolean connectEnabled,
        String email,
        String publishableKey,
        String connectedAccountId) {
}
