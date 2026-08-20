package com.agenticform.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Configuration de la page Paiement (persistée dans pages_json).
 */
public record PaymentConfigDto(
        String paymentType,
        String productName,
        String productDescription,
        BigDecimal amount,
        String currency,
        String billingInterval,
        String imageUrl,
        String checkoutTitle,
        Boolean allowDiscountCodes,
        Boolean collectEmails,
        Boolean includeFreeTrial,
        String freeTrialDays,
        Boolean suggestPresetAmount,
        BigDecimal presetAmount,
        Boolean defineLimits,
        BigDecimal minAmount,
        BigDecimal maxAmount,
        Boolean prefillFields,
        String prefillEmailRef,
        String prefillNameRef,
        String prefillPhoneRef,
        String prefillDiscountRef,
        List<String> paymentMethods,
        String stripePaymentDescription,
        String buttonText,
        Boolean showTestimonial,
        Map<String, Object> testimonial,
        Boolean showWarning,
        String warningText,
        Boolean sendReceipt) {
}
