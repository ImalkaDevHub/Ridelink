package com.ridelink.fare_payment_service.util;

import java.math.BigDecimal;
import java.util.Locale;

// Every input and intermediate value of one fare calculation, so callers
// can store and show exactly how the total was reached.
public record FareBreakdown(
        String vehicleType,
        BigDecimal distanceKm,
        BigDecimal durationMin,
        BigDecimal baseFare,
        BigDecimal perKmRate,
        BigDecimal perMinRate,
        BigDecimal distanceCharge,
        BigDecimal timeCharge,
        BigDecimal subtotal,
        BigDecimal minimumFare,
        boolean minimumApplied,
        BigDecimal multiplier,
        BigDecimal total,
        String currency) {

    // e.g. "max(500.00, 300.00 + 120.00/km x 5.00 km + 10.00/min x 12.00 min = 1020.00) x 1.0 (SEDAN) = 1020.00 LKR"
    public String describe() {
        return String.format(Locale.ROOT,
                "max(%s, %s + %s/km x %s km + %s/min x %s min = %s) x %s (%s) = %s %s",
                money(minimumFare), money(baseFare), money(perKmRate), money(distanceKm),
                money(perMinRate), money(durationMin), money(subtotal),
                multiplier.toPlainString(), vehicleType, money(total), currency);
    }

    private static String money(BigDecimal value) {
        return value.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }
}
