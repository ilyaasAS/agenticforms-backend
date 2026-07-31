package com.agenticform.exception;

public class UserNotFoundByEmailException extends RuntimeException {

    public UserNotFoundByEmailException(String email) {
        super("No user found for email: " + email);
    }
}
