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

    public double calculate(double distanceKm, double durationMin, String vehicleType) {
        if (distanceKm <= 0 || durationMin <= 0) {
            throw new IllegalArgumentException("distanceKm and durationMin must both be positive");
        }

        double finalBaseFare = this.baseFare;
        double finalPerKm = this.perKm;
        double finalPerMin = this.perMin;

        if ("Bike".equalsIgnoreCase(vehicleType)) {
            finalBaseFare = 40.0;
            finalPerKm = 20.0;
            finalPerMin = 5.0;
        } else if ("Tuk".equalsIgnoreCase(vehicleType)) {
            finalBaseFare = 50.0;
            finalPerKm = 30.0;
            finalPerMin = 8.0;
        } else if ("Van".equalsIgnoreCase(vehicleType)) {
            finalBaseFare = 150.0;
            finalPerKm = 75.0;
            finalPerMin = 15.0;
        }

        double fare = finalBaseFare + (finalPerKm * distanceKm) + (finalPerMin * durationMin);
        return Math.round(fare * 100.0) / 100.0;
    }

    public String describeBreakdown(double distanceKm, double durationMin, String vehicleType) {
        double fare = calculate(distanceKm, durationMin, vehicleType);
        
        double finalBaseFare = this.baseFare;
        double finalPerKm = this.perKm;
        double finalPerMin = this.perMin;

        if ("Bike".equalsIgnoreCase(vehicleType)) {
            finalBaseFare = 40.0;
            finalPerKm = 20.0;
            finalPerMin = 5.0;
        } else if ("Tuk".equalsIgnoreCase(vehicleType)) {
            finalBaseFare = 50.0;
            finalPerKm = 30.0;
            finalPerMin = 8.0;
        } else if ("Van".equalsIgnoreCase(vehicleType)) {
            finalBaseFare = 150.0;
            finalPerKm = 75.0;
            finalPerMin = 15.0;
        }
        
        return String.format(Locale.ROOT,
                "%.2f (base) + %.2f (%.2f/km x %.2f km) + %.2f (%.2f/min x %.2f min) = %.2f",
                finalBaseFare,
                finalPerKm * distanceKm, finalPerKm, distanceKm,
                finalPerMin * durationMin, finalPerMin, durationMin,
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
