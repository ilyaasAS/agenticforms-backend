package com.agenticform.exception;

/**
 * Tentative de liaison OAuth vers un compte local dont l'e-mail n'est pas encore vérifié.
 */
public class OAuthLinkRequiresVerifiedEmailException extends RuntimeException {

    public OAuthLinkRequiresVerifiedEmailException() {
        super("OAuth link requires a verified local email");
    }
}
