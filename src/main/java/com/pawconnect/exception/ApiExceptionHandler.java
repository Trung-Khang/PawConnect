package com.pawconnect.exception;

import java.time.Instant;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(ResourceConflictException.class)
    ResponseEntity<Map<String, Object>> conflict(ResourceConflictException exception) {
        return response(HttpStatus.CONFLICT, exception.getMessage());
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    ResponseEntity<Map<String, Object>> notFound(ResourceNotFoundException exception) {
        return response(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<Map<String, Object>> forbidden(AccessDeniedException exception) {
        return response(HttpStatus.FORBIDDEN, "You do not have permission to perform this action");
    }

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<Map<String, Object>> unauthorized(AuthenticationException exception) {
        return response(HttpStatus.UNAUTHORIZED, "Authentication failed");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String, Object>> validation(MethodArgumentNotValidException exception) {
        Map<String, String> fields = new java.util.LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors()
                .forEach(error -> fields.putIfAbsent(error.getField(), error.getDefaultMessage()));
        return ResponseEntity.badRequest().body(Map.of("timestamp", Instant.now(), "status", 400, "errors", fields));
    }

    @ExceptionHandler(BusinessException.class)
    ResponseEntity<Map<String, Object>> businessException(BusinessException exception) {
        return response(HttpStatus.CONFLICT, exception.getMessage());
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Map<String, Object>> generalException(Exception exception) {
        return response(HttpStatus.INTERNAL_SERVER_ERROR, exception.getMessage());
    }

    private ResponseEntity<Map<String, Object>> response(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of("timestamp", Instant.now(), "status", status.value(), "message", message));
    }
}
