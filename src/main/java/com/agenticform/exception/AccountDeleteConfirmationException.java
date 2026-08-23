package com.agenticform.exception;

public class AccountDeleteConfirmationException extends RuntimeException {

    public AccountDeleteConfirmationException() {
        super("L'e-mail de confirmation ne correspond pas à votre compte.");
    }
}
