package com.agenticform.exception;

public class FormNotAvailableException extends RuntimeException {

    public FormNotAvailableException() {
        super("Form not available for public access");
    }

    public FormNotAvailableException(Long formId) {
        super("Form not available for public access: id=" + formId);
    }
}
