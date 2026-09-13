package com.ridelink.fare_payment_service.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Locale;

// Single source of truth for the fare formula, reused by both the estimate
// and final-calculation flows:
//
//   finalFare = baseFare + (perKm * distanceKm) + (perMin * durationMin)
//
// rounded to 2 decimal places. Rates are configured via
// fare.base-fare / fare.per-km / fare.per-min in application.properties.
//
// Constructor injection (rather than this project's usual field @Autowired)
// is used deliberately so the formula can be unit tested with plain
// `new FareCalculator(...)` calls, without needing a Spring context.
@Component
public class FareCalculator {

    private final double baseFare;
    private final double perKm;
    private final double perMin;

    public FareCalculator(
            @Value("${fare.base-fare}") double baseFare,
            @Value("${fare.per-km}") double perKm,
            @Value("${fare.per-min}") double perMin) {
        this.baseFare = baseFare;
        this.perKm = perKm;
        this.perMin = perMin;
    }

    public double calculate(double distanceKm, double durationMin) {
        if (distanceKm <= 0 || durationMin <= 0) {
            throw new IllegalArgumentException("distanceKm and durationMin must both be positive");
        }

        double fare = baseFare + (perKm * distanceKm) + (perMin * durationMin);
        return Math.round(fare * 100.0) / 100.0;
    }

    // Human-readable statement of the formula as applied to a specific trip,
    // used on receipts so the calculation is fully traceable.
    public String describeBreakdown(double distanceKm, double durationMin) {
        double fare = calculate(distanceKm, durationMin);
        return String.format(Locale.ROOT,
                "%.2f (base) + %.2f (%.2f/km x %.2f km) + %.2f (%.2f/min x %.2f min) = %.2f",
                baseFare,
                perKm * distanceKm, perKm, distanceKm,
                perMin * durationMin, perMin, durationMin,
                fare);
    }

    public double getBaseFare() {
        return baseFare;
    }

    public double getPerKm() {
        return perKm;
    }

    public double getPerMin() {
        return perMin;
    }
}
