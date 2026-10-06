package com.applyflow.exception;

import org.springframework.http.HttpStatus;

public class NotFoundException extends ApiException {

    public NotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, "NOT_FOUND", message);
    }

    public static NotFoundException of(String entity, Object id) {
        return new NotFoundException(entity + " " + id + " was not found.");
    }
}
