package com.agenticform.exception;

public class OAuthLinkNotFoundException extends RuntimeException {

    public OAuthLinkNotFoundException(String provider) {
        super("No linked account for provider: " + provider);
    }
}
