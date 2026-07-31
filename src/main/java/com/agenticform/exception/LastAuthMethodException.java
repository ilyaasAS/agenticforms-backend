package com.agenticform.exception;

public class LastAuthMethodException extends RuntimeException {

    public LastAuthMethodException() {
        super("Cannot unlink the last authentication method");
    }
}
