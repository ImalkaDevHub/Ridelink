package com.ridelink.fare_payment_service.util;

import com.ridelink.fare_payment_service.config.FareProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

// Single source of truth for the fare rule, reused by estimates, final
// fares and payments:
//
//   fare = max(minimumFare, baseFare + perKm * distanceKm + perMin * durationMin)
//          * multiplier[vehicleType]
//
// All money is BigDecimal (LKR) and the total is rounded to 2 decimal
// places, HALF_UP. Rates and multipliers come from the fare.* properties
// (see FareProperties / application.properties).
//
// Constructor injection (rather than this project's usual field @Autowired)
// is used deliberately so the formula can be unit tested with plain
// `new FareCalculator(...)` calls, without needing a Spring context.
@Component
public class FareCalculator {

    private static final BigDecimal LEGACY_DEFAULT_MULTIPLIER = BigDecimal.ONE;

    private final FareProperties properties;

    public FareCalculator(FareProperties properties) {
        this.properties = properties;
    }

    // Strict: unknown vehicle types are rejected.
    public FareBreakdown calculate(BigDecimal distanceKm, BigDecimal durationMin, String vehicleType) {
        BigDecimal multiplier = properties.multiplierFor(vehicleType)
                .orElseThrow(() -> new InvalidVehicleTypeException(
                        "vehicleType must be one of " + String.join(", ", supportedVehicleTypes())));
        return calculate(distanceKm, durationMin, vehicleType, multiplier);
    }

    // Lenient: unknown vehicle types use multiplier 1.0. Only for the legacy
    // POST /api/payments/finalize call, which accepted any vehicle type.
    public FareBreakdown calculateLenient(BigDecimal distanceKm, BigDecimal durationMin, String vehicleType) {
        BigDecimal multiplier = properties.multiplierFor(vehicleType).orElse(LEGACY_DEFAULT_MULTIPLIER);
        return calculate(distanceKm, durationMin, vehicleType, multiplier);
    }

    public boolean isSupported(String vehicleType) {
        return properties.multiplierFor(vehicleType).isPresent();
    }

    public java.util.List<String> supportedVehicleTypes() {
        return properties.getMultipliers().keySet().stream()
                .map(key -> key.toUpperCase(Locale.ROOT))
                .toList();
    }

    private FareBreakdown calculate(BigDecimal distanceKm, BigDecimal durationMin, String vehicleType,
                                    BigDecimal multiplier) {
        if (distanceKm == null || distanceKm.signum() <= 0) {
            throw new IllegalArgumentException("distanceKm must be positive");
        }
        if (durationMin == null || durationMin.signum() < 0) {
            throw new IllegalArgumentException("durationMin must not be negative");
        }

        BigDecimal distanceCharge = properties.getPerKm().multiply(distanceKm);
        BigDecimal timeCharge = properties.getPerMin().multiply(durationMin);
        BigDecimal subtotal = properties.getBaseFare().add(distanceCharge).add(timeCharge);
        boolean minimumApplied = subtotal.compareTo(properties.getMinimumFare()) < 0;
        BigDecimal charged = minimumApplied ? properties.getMinimumFare() : subtotal;
        BigDecimal total = charged.multiply(multiplier).setScale(2, RoundingMode.HALF_UP);

        return new FareBreakdown(
                vehicleType == null ? null : vehicleType.trim().toUpperCase(Locale.ROOT),
                distanceKm,
                durationMin,
                properties.getBaseFare(),
                properties.getPerKm(),
                properties.getPerMin(),
                distanceCharge.setScale(2, RoundingMode.HALF_UP),
                timeCharge.setScale(2, RoundingMode.HALF_UP),
                subtotal.setScale(2, RoundingMode.HALF_UP),
                properties.getMinimumFare(),
                minimumApplied,
                multiplier,
                total,
                properties.getCurrency());
    }

    public static class InvalidVehicleTypeException extends RuntimeException {
        public InvalidVehicleTypeException(String message) {
            super(message);
        }
    }
}
