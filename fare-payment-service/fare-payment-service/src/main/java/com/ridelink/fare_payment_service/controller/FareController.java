package com.ridelink.fare_payment_service.controller;

import com.ridelink.fare_payment_service.dto.FareEstimateRequest;
import com.ridelink.fare_payment_service.dto.FareEstimateResponse;
import com.ridelink.fare_payment_service.dto.FinalFareRequest;
import com.ridelink.fare_payment_service.dto.PaymentResponse;
import com.ridelink.fare_payment_service.dto.ReceiptResponse;
import com.ridelink.fare_payment_service.service.FareService;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
public class FareController {

    @Autowired
    private FareService fareService;

    // Controller methods return ResponseEntity<?> because a single method
    // can return either the success DTO or an error body - springdoc can't
    // infer a schema from a wildcard generic type, so each @ApiResponse
    // below spells out the real status code and schema explicitly.

    // Public - fare estimates are often shown pre-login in real apps.
    @PostMapping("/api/fares/estimate")
    @SecurityRequirements
    @ApiResponse(responseCode = "200", description = "Estimate calculated",
            content = @Content(schema = @Schema(implementation = FareEstimateResponse.class)))
    @ApiResponse(responseCode = "400", description = "Validation failed or malformed JSON")
    public ResponseEntity<?> estimate(@Valid @RequestBody FareEstimateRequest request) {
        return ResponseEntity.ok(fareService.estimate(request));
    }

    // Interservice call: Ride Management invokes this when a ride is marked
    // COMPLETED, forwarding the ride's distance/duration and passenger, so a
    // payment can be recorded exactly once per ride.
    @PostMapping("/api/payments/finalize")
    @ApiResponse(responseCode = "201", description = "Payment recorded",
            content = @Content(schema = @Schema(implementation = PaymentResponse.class)))
    @ApiResponse(responseCode = "400", description = "Validation failed or malformed JSON")
    @ApiResponse(responseCode = "409", description = "A payment already exists for this ride")
    public ResponseEntity<?> finalizePayment(@Valid @RequestBody FinalFareRequest request) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(fareService.calculateFinalFare(request));
        } catch (FareService.DuplicatePaymentException e) {
            return errorResponse(HttpStatus.CONFLICT, "DUPLICATE_PAYMENT", e.getMessage());
        }
    }

    @GetMapping("/api/payments/{rideId}/status")
    @ApiResponse(responseCode = "200", description = "Payment found",
            content = @Content(schema = @Schema(implementation = PaymentResponse.class)))
    @ApiResponse(responseCode = "404", description = "No payment for this rideId")
    public ResponseEntity<?> getPaymentStatus(@PathVariable String rideId) {
        try {
            return ResponseEntity.ok(fareService.getPaymentStatus(rideId));
        } catch (FareService.PaymentNotFoundException e) {
            return errorResponse(HttpStatus.NOT_FOUND, "PAYMENT_NOT_FOUND", e.getMessage());
        }
    }

    @GetMapping("/api/payments/{rideId}/receipt")
    @ApiResponse(responseCode = "200", description = "Receipt with the formula breakdown",
            content = @Content(schema = @Schema(implementation = ReceiptResponse.class)))
    @ApiResponse(responseCode = "404", description = "No completed payment for this rideId")
    public ResponseEntity<?> getReceipt(@PathVariable String rideId) {
        try {
            return ResponseEntity.ok(fareService.getReceipt(rideId));
        } catch (FareService.PaymentNotFoundException e) {
            return errorResponse(HttpStatus.NOT_FOUND, "PAYMENT_NOT_FOUND", e.getMessage());
        }
    }

    private ResponseEntity<Map<String, String>> errorResponse(HttpStatus status, String code, String message) {
        Map<String, String> body = new HashMap<>();
        body.put("code", code);
        body.put("message", message);
        return ResponseEntity.status(status).body(body);
    }
}
