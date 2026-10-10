package com.ridelink.driver_service.model;

import java.util.Locale;
import java.util.Optional;

// Accepted vehicle types. Driver.vehicleType stays a String (stored
// upper-case) so existing documents keep loading; this enum is only the
// whitelist. CAR and BIKE are kept because existing drivers and the
// frontend's sign-up form already use them.
public enum VehicleType {
    SEDAN,
    HATCHBACK,
    VAN,
    TUK,
    CAR,
    BIKE;

    // Regex form of the whitelist for bean validation (case-insensitive).
    public static final String PATTERN = "(?i)^(SEDAN|HATCHBACK|VAN|TUK|CAR|BIKE)$";
    public static final String MESSAGE = "vehicleType must be one of SEDAN, HATCHBACK, VAN, TUK, CAR, BIKE";

    public static Optional<VehicleType> parse(String value) {
        if (value == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(valueOf(value.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
