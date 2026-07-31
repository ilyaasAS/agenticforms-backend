package com.agenticform.exception;

public class EmailAlreadyExistsException extends RuntimeException {

    public EmailAlreadyExistsException(String email) {
        super("Ce compte est déjà inscrit. Veuillez vous connecter.");
    }
}
