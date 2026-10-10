package com.ridelink.fare_payment_service.dto;

import java.math.BigDecimal;
import java.time.Instant;

// The first nine fields are the original receipt (the frontend reads them);
// the rest were added later. fareBreakdown is null for payments recorded
// before itemised fare records existed.
public record ReceiptResponse(
        String rideId,
        String passengerId,
        BigDecimal finalFare,
        double distanceKm,
        double durationMin,
        String paymentMethod,
        String status,
        Instant completedAt,
        String breakdown,
        String receiptNumber,
        String paymentId,
        String method,
        String currency,
        Instant timestamp,
        FareRecordResponse fareBreakdown) {
}
