package com.bss.application.exception.handler;

import com.bss.application.exception.ResourceNotFoundException;
import com.bss.domain.account.InsufficientBalanceException;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.context.request.WebRequest;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RestExceptionHandlerTest {

    private final RestExceptionHandler exceptionHandler = new RestExceptionHandler();
    private final WebRequest webRequest = mock(WebRequest.class);

    @Test
    @DisplayName("Should handle ResourceNotFoundException and return 404")
    void shouldHandleResourceNotFoundException() {
        ResourceNotFoundException ex = new ResourceNotFoundException("User not found");
        
        ResponseEntity<Object> response = exceptionHandler.handleResourceNotFound(ex, webRequest);
        
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertNotNull(body);
        assertEquals("User not found", body.get("error"));
    }

    @Test
    @DisplayName("Should handle RequestNotPermitted (Rate Limit) and return 429")
    void shouldHandleRequestNotPermitted() {
        RequestNotPermitted ex = mock(RequestNotPermitted.class);
        when(ex.getMessage()).thenReturn("Rate limit exceeded");
        when(webRequest.getDescription(false)).thenReturn("uri=/api/test");

        ResponseEntity<Object> response = exceptionHandler.handleRequestNotPermitted(ex, webRequest);

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, response.getStatusCode());
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertNotNull(body);
        assertEquals("Too many requests. Please try again later.", body.get("error"));
    }

    @Test
    @DisplayName("Should handle InsufficientBalanceException and return 422")
    void shouldHandleInsufficientBalanceException() {
        InsufficientBalanceException ex = new InsufficientBalanceException("Not enough funds");

        ResponseEntity<Object> response = exceptionHandler.handleInsufficientBalance(ex, webRequest);

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.getStatusCode());
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertNotNull(body);
        assertEquals("Not enough funds", body.get("error"));
    }

    @Test
    @DisplayName("Should handle IllegalArgumentException and return 400")
    void shouldHandleIllegalArgumentException() {
        IllegalArgumentException ex = new IllegalArgumentException("Invalid argument");

        ResponseEntity<Object> response = exceptionHandler.handleIllegalArgument(ex, webRequest);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertNotNull(body);
        assertEquals("Invalid argument", body.get("error"));
    }

    @Test
    @DisplayName("Should handle SecurityException and return 403")
    void shouldHandleSecurityException() {
        SecurityException ex = new SecurityException("Not authorized");

        ResponseEntity<Object> response = exceptionHandler.handleSecurityException(ex, webRequest);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertNotNull(body);
        assertEquals("Access Denied: Not authorized", body.get("error"));
    }

    @Test
    @DisplayName("Should handle DataIntegrityViolationException and return 409")
    void shouldHandleDataIntegrityViolationException() {
        DataIntegrityViolationException ex = new DataIntegrityViolationException("Duplicate key");

        ResponseEntity<Object> response = exceptionHandler.handleDataIntegrityViolation(ex, webRequest);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertNotNull(body);
        assertEquals("Data conflict: The resource you are trying to create or update violates a unique constraint (e.g., duplicate email, document, or ID).", body.get("error"));
    }

    @Test
    @DisplayName("Should handle generic Exception and return 500")
    void shouldHandleGenericException() {
        Exception ex = new RuntimeException("Unexpected error");

        ResponseEntity<Object> response = exceptionHandler.handleAllUncaughtException(ex, webRequest);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertNotNull(body);
        assertEquals("An unexpected internal server error has occurred.", body.get("error"));
    }
}
