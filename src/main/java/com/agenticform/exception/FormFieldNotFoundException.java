package com.agenticform.exception;

public class FormFieldNotFoundException extends RuntimeException {

    public FormFieldNotFoundException() {
        super("Form field not found");
    }

    public FormFieldNotFoundException(Long fieldId) {
        super("Form field not found: id=" + fieldId);
    }
}
