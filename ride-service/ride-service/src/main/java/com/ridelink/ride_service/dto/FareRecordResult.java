package com.ridelink.ride_service.dto;

import java.math.BigDecimal;

// The only field ride-service reads from POST /api/fares/final.
public record FareRecordResult(BigDecimal totalFare) {
}
