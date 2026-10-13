package com.messenger.web;

import com.messenger.exception.PremiumTierRequiredException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex) {
        return error(HttpStatus.BAD_REQUEST, ex.getMessage(), null);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalState(IllegalStateException ex) {
        return error(HttpStatus.CONFLICT, ex.getMessage(), null);
    }

    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<Map<String, Object>> handleSecurity(SecurityException ex) {
        return error(HttpStatus.FORBIDDEN, ex.getMessage(), null);
    }

    /**
     * Premium gating. Emits HTTP 403 with a {@code feature} hint so the client
     * can open the upgrade modal with the correct copy.
     */
    @ExceptionHandler(PremiumTierRequiredException.class)
    public ResponseEntity<Map<String, Object>> handlePremium(PremiumTierRequiredException ex) {
        return error(HttpStatus.FORBIDDEN, ex.getMessage(), ex.getFeature());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getField() + " " + err.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return error(HttpStatus.BAD_REQUEST, message, null);
    }

    private ResponseEntity<Map<String, Object>> error(HttpStatus status, String message, String feature) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", message);
        if (feature != null) {
            body.put("feature", feature);
        }
        body.put("timestamp", Instant.now().toString());
        return ResponseEntity.status(status).body(body);
    }
}

