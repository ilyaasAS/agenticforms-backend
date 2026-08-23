package com.agenticform.exception;

public class AdminCommandForbiddenException extends RuntimeException {

    public AdminCommandForbiddenException(String message) {
        super(message);
    }
}
