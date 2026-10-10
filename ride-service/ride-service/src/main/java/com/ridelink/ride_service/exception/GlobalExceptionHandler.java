package com.ridelink.ride_service.exception;

import com.ridelink.ride_service.client.DriverClient;
import com.ridelink.ride_service.client.FareClient;
import com.ridelink.ride_service.service.RideService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

// Maps every failure to the {"code": ..., "message": ...} shape used by all
// 4 services, plus an optional "details" object. Replaces the try/catch
// blocks that used to live in RideController; the codes are unchanged.
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // "message" keeps its original "field: error; field: error" form for
    // existing clients; "details" adds the same errors keyed by field.
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining("; "));

        Map<String, Object> details = e.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        error -> String.valueOf(error.getDefaultMessage()),
                        (first, second) -> first + "; " + second,
                        LinkedHashMap::new));

        return errorResponse(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message, details);
    }

    @ExceptionHandler(DriverClient.InvalidVehicleTypeException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidVehicleType(DriverClient.InvalidVehicleTypeException e) {
        return errorResponse(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "vehicleType: " + e.getMessage(),
                Map.of("vehicleType", e.getMessage()));
    }

    @ExceptionHandler(RideService.InvalidStatusFilterException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidStatusFilter(RideService.InvalidStatusFilterException e) {
        return errorResponse(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "status: " + e.getMessage(),
                Map.of("status", e.getMessage()));
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

    @ExceptionHandler(RideService.NotRideParticipantException.class)
    public ResponseEntity<Map<String, Object>> handleNotParticipant(RideService.NotRideParticipantException e) {
        return errorResponse(HttpStatus.FORBIDDEN, "FORBIDDEN", e.getMessage());
    }

    @ExceptionHandler(RideService.RideNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleRideNotFound(RideService.RideNotFoundException e) {
        return errorResponse(HttpStatus.NOT_FOUND, "RIDE_NOT_FOUND", e.getMessage());
    }

    @ExceptionHandler(RideService.InvalidStatusTransitionException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidTransition(RideService.InvalidStatusTransitionException e) {
        return errorResponse(HttpStatus.CONFLICT, "INVALID_STATUS_TRANSITION", e.getMessage());
    }

    @ExceptionHandler(RideService.NoDriverAvailableException.class)
    public ResponseEntity<Map<String, Object>> handleNoDriver(RideService.NoDriverAvailableException e) {
        return errorResponse(HttpStatus.CONFLICT, "NO_DRIVER_AVAILABLE", e.getMessage());
    }

    @ExceptionHandler(DriverClient.DriverServiceUnavailableException.class)
    public ResponseEntity<Map<String, Object>> handleDriverDown(DriverClient.DriverServiceUnavailableException e) {
        return errorResponse(HttpStatus.SERVICE_UNAVAILABLE, "DRIVER_SERVICE_UNAVAILABLE",
                "A driver cannot be assigned now. Please retry.");
    }

    @ExceptionHandler(FareClient.FareServiceUnavailableException.class)
    public ResponseEntity<Map<String, Object>> handleFareDown(FareClient.FareServiceUnavailableException e) {
        return errorResponse(HttpStatus.SERVICE_UNAVAILABLE, "FARE_SERVICE_UNAVAILABLE",
                "The final fare cannot be calculated now. The ride was not completed - please retry.");
    }

    // Fare Service answered but refused the request (e.g. a 4xx) - not
    // retryable as-is, so not a 503.
    @ExceptionHandler(FareClient.FareRejectedException.class)
    public ResponseEntity<Map<String, Object>> handleFareRejected(FareClient.FareRejectedException e) {
        return errorResponse(HttpStatus.BAD_GATEWAY, "FARE_REJECTED", e.getMessage());
    }

    @ExceptionHandler(RideService.PaymentFailedException.class)
    public ResponseEntity<Map<String, Object>> handlePaymentFailed(RideService.PaymentFailedException e) {
        return errorResponse(HttpStatus.SERVICE_UNAVAILABLE, "PAYMENT_FAILED", e.getMessage());
    }

    // Spring's own web exceptions (unknown path, wrong method, bad path
    // variable type, ...) keep their status; anything else is an unexpected
    // error - logged, and answered without internals.
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnexpected(Exception e) {
        if (e instanceof ErrorResponse errorResponse) {
            HttpStatusCode status = errorResponse.getStatusCode();
            HttpStatus resolved = HttpStatus.resolve(status.value());
            String code = resolved != null ? resolved.name() : "ERROR";
            return errorResponse(resolved != null ? resolved : HttpStatus.BAD_REQUEST, code,
                    errorResponse.getBody().getDetail() != null ? errorResponse.getBody().getDetail() : code);
        }
        log.error("Unexpected error", e);
        return errorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "An unexpected error occurred.");
    }

    private ResponseEntity<Map<String, Object>> errorResponse(HttpStatus status, String code, String message) {
        return errorResponse(status, code, message, null);
    }

    private ResponseEntity<Map<String, Object>> errorResponse(HttpStatus status, String code, String message,
                                                              Map<String, Object> details) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", code);
        body.put("message", message);
        if (details != null) {
            body.put("details", details);
        }
        return ResponseEntity.status(status).body(body);
    }
}
