package com.agenticform.exception;

public class GoogleCalendarIntegrationException extends RuntimeException {

    public GoogleCalendarIntegrationException(String message) {
        super(message);
    }

    public GoogleCalendarIntegrationException(String message, Throwable cause) {
        super(message, cause);
    }
}
