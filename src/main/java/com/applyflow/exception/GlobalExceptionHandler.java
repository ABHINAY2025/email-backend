package com.applyflow.exception;

import com.applyflow.dto.CommonDtos.ApiError;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/** Maps every exception to the contract's ApiError shape. Never leaks stack traces or SQL. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> handleApi(ApiException ex) {
        if (ex instanceof ImapException && ex.getCause() != null) {
            log.info("IMAP error {}: {}", ex.getCode(), ex.getCause().getClass().getSimpleName());
        }
        Map<String, String> fieldErrors = ex instanceof FieldValidationException fve ? fve.getFieldErrors() : null;
        return build(ex.getStatus(), ex.getCode(), ex.getMessage(), fieldErrors);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(fe -> fieldErrors.putIfAbsent(fe.getField(),
                        fe.getDefaultMessage() == null ? "is invalid" : fe.getDefaultMessage()));
        ex.getBindingResult().getGlobalErrors()
                .forEach(ge -> fieldErrors.putIfAbsent(ge.getObjectName(), String.valueOf(ge.getDefaultMessage())));
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Some fields are invalid.", fieldErrors);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraint(ConstraintViolationException ex) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        ex.getConstraintViolations().forEach(v -> {
            String path = v.getPropertyPath() == null ? "value" : v.getPropertyPath().toString();
            int dot = path.lastIndexOf('.');
            fieldErrors.putIfAbsent(dot >= 0 ? path.substring(dot + 1) : path, v.getMessage());
        });
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Some fields are invalid.", fieldErrors);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiError> handleMethodValidation(HandlerMethodValidationException ex) {
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Some parameters are invalid.", null);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException ex) {
        if (ex.getCause() instanceof com.fasterxml.jackson.databind.exc.InvalidFormatException ife
                && !ife.getPath().isEmpty()) {
            String field = ife.getPath().get(ife.getPath().size() - 1).getFieldName();
            String msg = ife.getTargetType() != null && ife.getTargetType().isEnum()
                    ? "must be one of " + java.util.Arrays.toString(ife.getTargetType().getEnumConstants())
                    : "has an invalid value";
            return build(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Some fields are invalid.",
                    Map.of(field == null ? "value" : field, msg));
        }
        return build(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "Malformed request body.", null);
    }

    @ExceptionHandler({MethodArgumentTypeMismatchException.class, MissingServletRequestParameterException.class})
    public ResponseEntity<ApiError> handleBadParam(Exception ex) {
        String name = ex instanceof MethodArgumentTypeMismatchException m ? m.getName()
                : ((MissingServletRequestParameterException) ex).getParameterName();
        return build(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "Invalid or missing parameter '" + name + "'.", null);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiError> handleMethod(HttpRequestMethodNotSupportedException ex) {
        return build(HttpStatus.METHOD_NOT_ALLOWED, "BAD_REQUEST", "Method not allowed.", null);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiError> handleMediaType(HttpMediaTypeNotSupportedException ex) {
        return build(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "BAD_REQUEST", "Unsupported content type.", null);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> handleNoResource(NoResourceFoundException ex) {
        return build(HttpStatus.NOT_FOUND, "NOT_FOUND", "Resource not found.", null);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleIntegrity(DataIntegrityViolationException ex) {
        log.warn("Data integrity violation: {}", ex.getMostSpecificCause().getClass().getSimpleName());
        return build(HttpStatus.CONFLICT, "CONFLICT", "The request conflicts with existing data.", null);
    }

    @ExceptionHandler({CannotCreateTransactionException.class, DataAccessResourceFailureException.class})
    public ResponseEntity<ApiError> handleDatabaseDown(Exception ex) {
        log.error("Database unavailable: {}", ex.getMessage());
        return build(HttpStatus.SERVICE_UNAVAILABLE, "DATABASE_ERROR",
                "The database is currently unavailable. Please try again shortly.", null);
    }

    @ExceptionHandler({AsyncRequestTimeoutException.class, AsyncRequestNotUsableException.class})
    public void handleAsyncTimeout() {
        // SSE stream ended; nothing to write.
    }

    @ExceptionHandler(IOException.class)
    public ResponseEntity<ApiError> handleIo(IOException ex) {
        // Typically a client disconnect (e.g. SSE "broken pipe"); avoid noisy stack traces.
        log.debug("I/O error while handling request: {}", ex.getMessage());
        return null;
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGeneric(Exception ex) {
        log.error("Unhandled error", ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "An unexpected error occurred. Please try again.", null);
    }

    private ResponseEntity<ApiError> build(HttpStatus status, String code, String message,
                                           Map<String, String> fieldErrors) {
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON)
                .body(new ApiError(status.value(), code, message, Instant.now(), fieldErrors));
    }
}
