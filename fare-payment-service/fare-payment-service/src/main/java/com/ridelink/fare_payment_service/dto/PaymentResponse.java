package com.ridelink.fare_payment_service.dto;

import com.ridelink.fare_payment_service.model.Payment;

import java.math.BigDecimal;
import java.time.Instant;

// The first seven fields are the original response (ride-service reads
// id/rideId/finalFare/status); the rest were added later.
public record PaymentResponse(
        String id,
        String rideId,
        BigDecimal finalFare,
        String status,
        String paymentMethod,
        Instant createdAt,
        Instant completedAt,
        String method,
        String cardLast4,
        String currency,
        String passengerId,
        String payerId,
        String receiptNumber,
        String failureReason) {

    public static PaymentResponse from(Payment payment) {
        return new PaymentResponse(
                payment.getId(),
                payment.getRideId(),
                payment.getFinalFare(),
                payment.getStatus().name(),
                payment.getPaymentMethod(),
                payment.getCreatedAt(),
                payment.getCompletedAt(),
                payment.getMethod() == null ? null : payment.getMethod().name(),
                payment.getCardLast4(),
                payment.getCurrency(),
                payment.getPassengerId(),
                payment.getPayerId(),
                payment.getReceiptNumber(),
                payment.getFailureReason());
    }
}
