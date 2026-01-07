package com.bss.application.exception.handler;

import com.bss.application.exception.ResourceNotFoundException;
import com.bss.domain.account.InsufficientBalanceException;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.HashMap;
import java.util.Map;

@ControllerAdvice
public class RestExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(RestExceptionHandler.class);
    private static final String ERROR_KEY = "error";

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Object> handleResourceNotFound(ResourceNotFoundException ex, WebRequest request) {
        log.warn("Resource not found: {}", ex.getMessage());
        return ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(Map.of(ERROR_KEY, ex.getMessage()));
    }

    @ExceptionHandler(RequestNotPermitted.class)
    public ResponseEntity<Object> handleRequestNotPermitted(RequestNotPermitted ex, WebRequest request) {
        if (log.isWarnEnabled()) {
            log.warn("Rate limit exceeded for {}: {}", request.getDescription(false), ex.getMessage());
        }
        return ResponseEntity
            .status(HttpStatus.TOO_MANY_REQUESTS)
            .body(Map.of(ERROR_KEY, "Too many requests. Please try again later."));
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, @NonNull HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error -> 
            errors.put(error.getField(), error.getDefaultMessage())
        );
        
        log.warn("DTO validation failed: {}", errors);
        return new ResponseEntity<>(errors, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(InsufficientBalanceException.class)
    protected ResponseEntity<Object> handleInsufficientBalance(InsufficientBalanceException ex, WebRequest request) {
        log.warn("Transfer rejected due to insufficient balance: {}", ex.getMessage());
        return new ResponseEntity<>(Map.of(ERROR_KEY, ex.getMessage()), HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    protected ResponseEntity<Object> handleIllegalArgument(IllegalArgumentException ex, WebRequest request) {
        log.warn("Business validation failed: {}", ex.getMessage());
        return new ResponseEntity<>(Map.of(ERROR_KEY, ex.getMessage()), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(SecurityException.class)
    protected ResponseEntity<Object> handleSecurityException(SecurityException ex, WebRequest request) {
        log.warn("Security check failed: {}", ex.getMessage());
        return new ResponseEntity<>(Map.of(ERROR_KEY, "Access Denied: " + ex.getMessage()), HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    protected ResponseEntity<Object> handleDataIntegrityViolation(DataIntegrityViolationException ex, WebRequest request) {
        log.warn("Data integrity violation: {}", ex.getMessage());
        // We return 409 Conflict because this usually means a unique constraint violation (e.g. duplicate email/document)
        return new ResponseEntity<>(Map.of(ERROR_KEY, "Data conflict: The resource you are trying to create or update violates a unique constraint (e.g., duplicate email, document, or ID)."), HttpStatus.CONFLICT);
    }

    @ExceptionHandler(Exception.class)
    protected ResponseEntity<Object> handleAllUncaughtException(Exception ex, WebRequest request) {
        log.error("An unhandled exception occurred", ex);
        return new ResponseEntity<>(Map.of(ERROR_KEY, "An unexpected internal server error has occurred."), HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
