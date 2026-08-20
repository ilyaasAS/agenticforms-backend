package com.agenticform.exception;

public class GoogleCalendarNotConfiguredException extends RuntimeException {

    public GoogleCalendarNotConfiguredException() {
        super("Google Calendar n’est pas configuré sur le serveur.");
    }
}
