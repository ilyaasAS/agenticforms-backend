package com.agenticform.exception;

public class CalendlyNotConfiguredException extends RuntimeException {

    public CalendlyNotConfiguredException() {
        super("Calendly n’est pas configuré sur le serveur.");
    }
}
