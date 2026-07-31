package com.agenticform.exception;

public class OAuthIdentityConflictException extends RuntimeException {

    public OAuthIdentityConflictException() {
        super("OAuth identity conflict for this email");
    }
}
