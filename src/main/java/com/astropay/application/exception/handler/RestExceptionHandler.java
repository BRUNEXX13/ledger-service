package com.astropay.application.exception.handler;

import com.astropay.application.exception.ResourceNotFoundException;
import com.astropay.domain.model.account.InsufficientBalanceException;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private static final String ERROR_KEY_HANDLE = "error";
    private static final String ERROR_KEY_RATE = "error";
    private static final String ERROR_KEY_INSUFICIENT = "error";
    private static final String ERROR_KEY_ILLEGAL = "error";
    private static final String ERROR_KEY_SECURITY = "error";
    private static final String ERROR_KEY_CAUGHT= "error";

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Object> handleResourceNotFound(ResourceNotFoundException ex) {
        log.warn("Resource not found: {}", ex.getMessage());
        return ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(Map.of(ERROR_KEY_HANDLE, ex.getMessage()));
    }

    @ExceptionHandler(RequestNotPermitted.class)
    public ResponseEntity<Object> handleRequestNotPermitted(RequestNotPermitted ex, WebRequest request) {
        if (log.isWarnEnabled()) {
            log.warn("Rate limit exceeded for {}: {}", request.getDescription(false), ex.getMessage());
        }
        return ResponseEntity
            .status(HttpStatus.TOO_MANY_REQUESTS)
            .body(Map.of(ERROR_KEY_RATE, "Too many requests. Please try again later."));
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, @NonNull HttpHeaders headers, @NonNull HttpStatusCode status, @NonNull WebRequest request) {
        
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error -> 
            errors.put(error.getField(), error.getDefaultMessage())
        );
        
        log.warn("DTO validation failed: {}", errors);
        return new ResponseEntity<>(errors, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(InsufficientBalanceException.class)
    protected ResponseEntity<Object> handleInsufficientBalance(InsufficientBalanceException ex) {
        log.warn("Transfer rejected due to insufficient balance: {}", ex.getMessage());
        return new ResponseEntity<>(Map.of(ERROR_KEY_INSUFICIENT, ex.getMessage()), HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    protected ResponseEntity<Object> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("Business validation failed: {}", ex.getMessage());
        return new ResponseEntity<>(Map.of(ERROR_KEY_ILLEGAL, ex.getMessage()), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(SecurityException.class)
    protected ResponseEntity<Object> handleSecurityException(SecurityException ex) {
        log.warn("Security check failed: {}", ex.getMessage());
        return new ResponseEntity<>(Map.of(ERROR_KEY_SECURITY, "Access Denied: " + ex.getMessage()), HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(Exception.class)
    protected ResponseEntity<Object> handleAllUncaughtException(Exception ex) {
        log.error("An unhandled exception occurred", ex);
        return new ResponseEntity<>(Map.of(ERROR_KEY_CAUGHT, "An unexpected internal server error has occurred."), HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
