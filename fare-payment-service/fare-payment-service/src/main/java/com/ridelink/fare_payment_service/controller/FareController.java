package com.ridelink.fare_payment_service.controller;

import com.ridelink.fare_payment_service.dto.FareEstimateRequest;
import com.ridelink.fare_payment_service.dto.FinalFareRequest;
import com.ridelink.fare_payment_service.service.FareService;
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

    @PostMapping("/api/fares/estimate")
    public ResponseEntity<?> estimate(@Valid @RequestBody FareEstimateRequest request) {
        return ResponseEntity.ok(fareService.estimate(request));
    }

    // Interservice call: Ride Management invokes this when a ride is marked
    // COMPLETED, forwarding the ride's distance/duration and passenger, so a
    // payment can be recorded exactly once per ride.
    @PostMapping("/api/payments/finalize")
    public ResponseEntity<?> finalizePayment(@Valid @RequestBody FinalFareRequest request) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(fareService.calculateFinalFare(request));
        } catch (FareService.DuplicatePaymentException e) {
            return errorResponse(HttpStatus.CONFLICT, "DUPLICATE_PAYMENT", e.getMessage());
        }
    }

    @GetMapping("/api/payments/{rideId}/status")
    public ResponseEntity<?> getPaymentStatus(@PathVariable String rideId) {
        try {
            return ResponseEntity.ok(fareService.getPaymentStatus(rideId));
        } catch (FareService.PaymentNotFoundException e) {
            return errorResponse(HttpStatus.NOT_FOUND, "PAYMENT_NOT_FOUND", e.getMessage());
        }
    }

    @GetMapping("/api/payments/{rideId}/receipt")
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
