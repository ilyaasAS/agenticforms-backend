package com.agenticform.exception;

/** Compte créé / géré uniquement via OAuth — pas de mot de passe local utilisable. */
public class PasswordLoginDisabledException extends RuntimeException {

    public static final String CODE = "PASSWORD_LOGIN_DISABLED";

    public PasswordLoginDisabledException() {
        super(CODE);
    }
}
