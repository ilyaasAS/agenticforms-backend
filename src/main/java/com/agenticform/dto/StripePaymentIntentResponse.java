package com.agenticform.dto;

public record StripePaymentIntentResponse(
        String clientSecret,
        String publishableKey,
        String paymentIntentId,
        String connectedAccountId,
        long amount,
        String currency,
        /** "payment" (PaymentIntent) ou "setup" (SetupIntent, essai gratuit). */
        String mode,
        String subscriptionId,
        /** Montant avant réduction (null si pas de code promo). */
        Long originalAmount,
        /** Ex. « -20 % » ou « -5,00 € ». */
        String discountLabel) {

    public StripePaymentIntentResponse(
            String clientSecret,
            String publishableKey,
            String paymentIntentId,
            String connectedAccountId,
            long amount,
            String currency) {
        this(clientSecret, publishableKey, paymentIntentId, connectedAccountId, amount, currency,
                "payment", null, null, null);
    }
}
