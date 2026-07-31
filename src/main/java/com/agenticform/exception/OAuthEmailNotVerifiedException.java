package com.agenticform.exception;

public class OAuthEmailNotVerifiedException extends RuntimeException {

    public OAuthEmailNotVerifiedException() {
        super("OAuth email is not verified");
    }
}
