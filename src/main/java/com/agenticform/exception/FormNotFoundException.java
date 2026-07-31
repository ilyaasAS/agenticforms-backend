package com.agenticform.exception;

public class FormNotFoundException extends RuntimeException {

    public FormNotFoundException() {
        super("Form not found");
    }

    public FormNotFoundException(Long formId) {
        super("Form not found: id=" + formId);
    }
}
