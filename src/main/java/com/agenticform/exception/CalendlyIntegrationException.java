package com.agenticform.exception;

public class CalendlyIntegrationException extends RuntimeException {

    public CalendlyIntegrationException(String message) {
        super(message);
    }

    public CalendlyIntegrationException(String message, Throwable cause) {
        super(message, cause);
    }
}
