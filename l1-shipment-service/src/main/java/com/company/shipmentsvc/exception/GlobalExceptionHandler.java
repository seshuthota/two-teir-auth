package com.company.shipmentsvc.exception;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(TokenValidationException.class)
    public ResponseEntity<Map<String, Object>> handleTokenValidation(TokenValidationException ex) {
        return ResponseEntity.status(ex.getHttpStatus()).body(Map.of(
                "status", ex.getHttpStatus(),
                "error", ex.getError(),
                "code", ex.getCode(),
                "message", ex.getMessage()
        ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneral(Exception ex) {
        return ResponseEntity.status(500).body(Map.of(
                "status", 500,
                "error", "INTERNAL_ERROR",
                "code", "SERVICE_ERROR",
                "message", "An unexpected error occurred"
        ));
    }
}
