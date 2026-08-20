package com.agenticform.dto;

public record ConfirmPaymentResponse(boolean confirmed, String paymentIntentId) {
}
