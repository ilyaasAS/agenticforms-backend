package com.agenticform.exception;

public class InvalidCurrentPasswordException extends RuntimeException {

    public InvalidCurrentPasswordException() {
        super("Mot de passe actuel incorrect.");
    }
}
