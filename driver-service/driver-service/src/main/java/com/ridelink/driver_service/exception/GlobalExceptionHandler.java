package com.ridelink.driver_service.exception;

import com.ridelink.driver_service.service.DriverService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

// Catches errors that never reach a controller's own try/catch: bean
// validdation failures on @Valid DTOs, @PreAuthorize access denials,
// unparseable request bodies, missing required parameters, and the domain
// exceptions thrown by DriverService. Keeps the same
// {"code": ..., "message": ...} shape used by all 4 services, plus an
// optional "details" object.
@RestControllerAdvice
public class GlobalExceptionHandler {

    // "message" keeps its original "field: error; field: error" form for
    // existing clients; "details" adds the same errors keyed by field.
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining("; "));

        Map<String, String> details = e.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        error -> String.valueOf(error.getDefaultMessage()),
                        (first, second) -> first + "; " + second,
                        LinkedHashMap::new));

        return errorResponse(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message, details);
    }

    // Vehicle type also arrives as a query parameter (eligible, reserve), so
    // it is checked in the service too and reported like a validation error.
    @ExceptionHandler(DriverService.InvalidVehicleTypeException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidVehicleType(DriverService.InvalidVehicleTypeException e) {
        return errorResponse(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "vehicleType: " + e.getMessage(),
                Map.of("vehicleType", e.getMessage()));
    }

    // Malformed/unparseable JSON fails during request body binding, before
    // @Valid ever runs - without this handler it would fall through to
    // Spring Boot's default error page instead of our error shape.
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleMalformedJson(HttpMessageNotReadableException e) {
        return errorResponse(HttpStatus.BAD_REQUEST, "MALFORMED_JSON", "Request body is missing or not valid JSON.");
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Map<String, Object>> handleMissingParameter(MissingServletRequestParameterException e) {
        return errorResponse(HttpStatus.BAD_REQUEST, "MISSING_PARAMETER", e.getMessage());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(AccessDeniedException e) {
        return errorResponse(HttpStatus.FORBIDDEN, "FORBIDDEN", "You do not have permission to perform this action.");
    }

    @ExceptionHandler(DriverService.DriverNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleDriverNotFound(DriverService.DriverNotFoundException e) {
        return errorResponse(HttpStatus.NOT_FOUND, "DRIVER_NOT_FOUND", e.getMessage());
    }

    @ExceptionHandler(DriverService.DuplicateVehicleException.class)
    public ResponseEntity<Map<String, Object>> handleDuplicateVehicle(DriverService.DuplicateVehicleException e) {
        return errorResponse(HttpStatus.CONFLICT, "VEHICLE_ALREADY_EXISTS", e.getMessage());
    }

    @ExceptionHandler(DriverService.NoDriverAvailableException.class)
    public ResponseEntity<Map<String, Object>> handleNoDriverAvailable(DriverService.NoDriverAvailableException e) {
        return errorResponse(HttpStatus.CONFLICT, "NO_DRIVER_AVAILABLE", e.getMessage());
    }

    // Any other unique-index violation on drivers is the email index.
    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<Map<String, Object>> handleDuplicateKey(DuplicateKeyException e) {
        return errorResponse(HttpStatus.CONFLICT, "EMAIL_ALREADY_EXISTS", "A driver with this email already exists.");
    }

    // @Version mismatch: someone else updated this driver in between.
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<Map<String, Object>> handleOptimisticLock(OptimisticLockingFailureException e) {
        return errorResponse(HttpStatus.CONFLICT, "CONCURRENT_UPDATE", "This driver was updated by another request. Please retry.");
    }

    private ResponseEntity<Map<String, Object>> errorResponse(HttpStatus status, String code, String message) {
        return errorResponse(status, code, message, null);
    }

    private ResponseEntity<Map<String, Object>> errorResponse(HttpStatus status, String code, String message,
                                                              Map<String, String> details) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", code);
        body.put("message", message);
        if (details != null) {
            body.put("details", details);
        }
        return ResponseEntity.status(status).body(body);
    }
}
