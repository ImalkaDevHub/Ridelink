package com.ridelink.ride_service.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

// Catches errors that never reach a controller's own try/catch: bean
// validation failures on @Valid DTOs, @PreAuthorize access denials,
// unparseable request bodies and missing required parameters.
// Keeps the same {"code": ..., "message": ...} shape used by all 4 services.
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining("; "));

        return errorResponse(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message);
    }

    // Malformed/unparseable JSON fails during request body binding, before
    // @Valid ever runs - without this handler it would fall through to
    // Spring Boot's default error page instead of our error shape.
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> handleMalformedJson(HttpMessageNotReadableException e) {
        return errorResponse(HttpStatus.BAD_REQUEST, "MALFORMED_JSON", "Request body is missing or not valid JSON.");
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Map<String, String>> handleMissingParameter(MissingServletRequestParameterException e) {
        return errorResponse(HttpStatus.BAD_REQUEST, "MISSING_PARAMETER", e.getMessage());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, String>> handleAccessDenied(AccessDeniedException e) {
        return errorResponse(HttpStatus.FORBIDDEN, "FORBIDDEN", "You do not have permission to perform this action.");
    }

    private ResponseEntity<Map<String, String>> errorResponse(HttpStatus status, String code, String message) {
        Map<String, String> body = new HashMap<>();
        body.put("code", code);
        body.put("message", message);
        return ResponseEntity.status(status).body(body);
    }
}
