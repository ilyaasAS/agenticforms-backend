package com.agenticform.exception;

public class SamePasswordException extends RuntimeException {

    public SamePasswordException() {
        super("Le nouveau mot de passe doit être différent de l'ancien");
    }
}
