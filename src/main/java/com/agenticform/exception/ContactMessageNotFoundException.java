package com.agenticform.exception;

public class ContactMessageNotFoundException extends RuntimeException {

    public ContactMessageNotFoundException(String id) {
        super("Contact message not found: " + id);
    }
}
