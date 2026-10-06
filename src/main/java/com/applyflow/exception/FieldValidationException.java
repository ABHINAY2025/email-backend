package com.applyflow.exception;

import org.springframework.http.HttpStatus;

import java.util.Map;

/** Manual validation failure with per-field messages (rendered as VALIDATION_FAILED). */
public class FieldValidationException extends ApiException {

    private final Map<String, String> fieldErrors;

    public FieldValidationException(Map<String, String> fieldErrors) {
        super(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Some fields are invalid.");
        this.fieldErrors = Map.copyOf(fieldErrors);
    }

    public static FieldValidationException of(String field, String message) {
        return new FieldValidationException(Map.of(field, message));
    }

    public Map<String, String> getFieldErrors() {
        return fieldErrors;
    }
}
