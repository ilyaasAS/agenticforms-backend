package com.agenticform.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ConfirmPaymentRequest(
        @NotBlank @Size(max = 128) String paymentIntentId,
        @Size(max = 64) String pageId,
        @Email @Size(max = 255) String email) {
}
