package com.agenticform.exception;

public class InvalidEmailVerificationTokenException extends RuntimeException {

    public InvalidEmailVerificationTokenException() {
        super("Invalid or expired email verification token");
    }
}
