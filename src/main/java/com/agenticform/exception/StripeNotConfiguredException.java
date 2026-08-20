package com.agenticform.exception;

public class StripeNotConfiguredException extends RuntimeException {

    public StripeNotConfiguredException() {
        super("Stripe n’est pas configuré sur le serveur.");
    }
}
