package com.agenticform.exception;

public class UserAccountNotFoundException extends RuntimeException {

    public UserAccountNotFoundException(Long id) {
        super("User not found: " + id);
    }
}
