package com.ridelink.fare_payment_service.exception;

import com.ridelink.fare_payment_service.service.PaymentService;
import com.ridelink.fare_payment_service.util.DistanceResolver;
import com.ridelink.fare_payment_service.util.FareCalculator;
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

// Maps every known failure to the {"code": ..., "message": ...} shape used
// by all 4 services, plus an optional "details" object: bean validation,
// unparseable bodies, missing parameters, access denials, and the fare and
// payment exceptions thrown by the services.
@RestControllerAdvice
public class GlobalExceptionHandler {

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

    @ExceptionHandler(FareCalculator.InvalidVehicleTypeException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidVehicleType(FareCalculator.InvalidVehicleTypeException e) {
        return errorResponse(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "vehicleType: " + e.getMessage(),
                Map.of("vehicleType", e.getMessage()));
    }

    @ExceptionHandler(PaymentService.MissingCardDetailsException.class)
    public ResponseEntity<Map<String, Object>> handleMissingCard(PaymentService.MissingCardDetailsException e) {
        return errorResponse(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "cardLast4: " + e.getMessage(),
                Map.of("cardLast4", e.getMessage()));
    }

    @ExceptionHandler(DistanceResolver.DistanceUnresolvedException.class)
    public ResponseEntity<Map<String, Object>> handleDistanceUnresolved(DistanceResolver.DistanceUnresolvedException e) {
        return errorResponse(HttpStatus.BAD_REQUEST, "DISTANCE_UNRESOLVED", e.getMessage());
    }

    // FareCalculator's own guard (non-positive distance, negative duration).
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException e) {
        return errorResponse(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", e.getMessage());
    }

    // Malformed/unparseable JSON fails during request body binding, before
    // @Valid ever runs - without this handler it would fall through to
    // Spring Boot's default error page instead of our error shape. This
    // also covers an unknown "method" value (not CARD/CASH).
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

    @ExceptionHandler(PaymentService.NotPayerException.class)
    public ResponseEntity<Map<String, Object>> handleNotPayer(PaymentService.NotPayerException e) {
        return errorResponse(HttpStatus.FORBIDDEN, "FORBIDDEN", e.getMessage());
    }

    @ExceptionHandler(PaymentService.FareNotFinalizedException.class)
    public ResponseEntity<Map<String, Object>> handleFareNotFinalized(PaymentService.FareNotFinalizedException e) {
        return errorResponse(HttpStatus.CONFLICT, "FARE_NOT_FINALIZED", e.getMessage());
    }

    @ExceptionHandler(PaymentService.PaymentAlreadyExistsException.class)
    public ResponseEntity<Map<String, Object>> handlePaymentAlreadyExists(PaymentService.PaymentAlreadyExistsException e) {
        return errorResponse(HttpStatus.CONFLICT, "PAYMENT_ALREADY_EXISTS", e.getMessage());
    }

    @ExceptionHandler(PaymentService.DuplicatePaymentException.class)
    public ResponseEntity<Map<String, Object>> handleDuplicatePayment(PaymentService.DuplicatePaymentException e) {
        return errorResponse(HttpStatus.CONFLICT, "DUPLICATE_PAYMENT", e.getMessage());
    }

    // The failed attempt is saved; its id is returned so the client can
    // show it, and the passenger may retry with another card.
    @ExceptionHandler(PaymentService.PaymentFailedException.class)
    public ResponseEntity<Map<String, Object>> handlePaymentFailed(PaymentService.PaymentFailedException e) {
        return errorResponse(HttpStatus.PAYMENT_REQUIRED, "PAYMENT_FAILED", e.getMessage(),
                Map.of("paymentId", e.getPaymentId(), "status", "FAILED"));
    }

    @ExceptionHandler(PaymentService.PaymentNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handlePaymentNotFound(PaymentService.PaymentNotFoundException e) {
        return errorResponse(HttpStatus.NOT_FOUND, "PAYMENT_NOT_FOUND", e.getMessage());
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
