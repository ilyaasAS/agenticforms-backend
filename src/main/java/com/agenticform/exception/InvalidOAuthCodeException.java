package com.agenticform.exception;

public class InvalidOAuthCodeException extends RuntimeException {

    public InvalidOAuthCodeException() {
        super("Invalid or expired OAuth code");
    }
}
