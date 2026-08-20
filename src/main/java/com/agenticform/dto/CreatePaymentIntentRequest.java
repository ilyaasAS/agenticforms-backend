package com.agenticform.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

public record CreatePaymentIntentRequest(
        @Size(max = 64) String pageId,
        @Email @Size(max = 255) String email,
        BigDecimal amount,
        @Size(max = 64) String discountCode) {
}
