package com.ridelink.fare_payment_service.util;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.Map;

// Works out the trip distance for an estimate, in this order:
//   1. distanceKm, if the caller gave one;
//   2. the Haversine (great-circle) distance between pickup and destination
//      coordinates, if all four are given;
//   3. the Haversine distance between two known place names from the small
//      static table below.
// Haversine is straight-line distance, so it under-estimates road distance -
// fine for an estimate. Final fares always use the distance reported when
// the ride is completed.
@Component
public class DistanceResolver {

    private static final double EARTH_RADIUS_KM = 6371.0088;

    // Approximate coordinates (lat, lng). Keys are lower-case.
    private static final Map<String, double[]> PLACES = Map.ofEntries(
            Map.entry("sliit", new double[]{6.9147, 79.9729}),
            Map.entry("malabe", new double[]{6.9040, 79.9550}),
            Map.entry("malabe junction", new double[]{6.9061, 79.9696}),
            Map.entry("kaduwela", new double[]{6.9330, 79.9840}),
            Map.entry("battaramulla", new double[]{6.9020, 79.9180}),
            Map.entry("rajagiriya", new double[]{6.9090, 79.8950}),
            Map.entry("nugegoda", new double[]{6.8649, 79.8997}),
            Map.entry("colombo fort", new double[]{6.9344, 79.8428}),
            Map.entry("kollupitiya", new double[]{6.9110, 79.8510}),
            Map.entry("bambalapitiya", new double[]{6.8890, 79.8560}),
            Map.entry("dehiwala", new double[]{6.8510, 79.8650}),
            Map.entry("kandy", new double[]{7.2906, 80.6337}));

    public ResolvedDistance resolve(BigDecimal distanceKm,
                                    Double pickupLat, Double pickupLng,
                                    Double destinationLat, Double destinationLng,
                                    String pickupName, String destinationName) {
        if (distanceKm != null) {
            return new ResolvedDistance(distanceKm, Source.PROVIDED);
        }
        if (pickupLat != null && pickupLng != null && destinationLat != null && destinationLng != null) {
            return new ResolvedDistance(haversineKm(pickupLat, pickupLng, destinationLat, destinationLng), Source.COORDINATES);
        }
        double[] from = lookup(pickupName);
        double[] to = lookup(destinationName);
        if (from != null && to != null) {
            return new ResolvedDistance(haversineKm(from[0], from[1], to[0], to[1]), Source.PLACE_TABLE);
        }
        throw new DistanceUnresolvedException(
                "Provide distanceKm, all four coordinates, or known place names (" + String.join(", ", knownPlaces()) + ").");
    }

    public BigDecimal haversineKm(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double km = 2 * EARTH_RADIUS_KM * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return BigDecimal.valueOf(km).setScale(2, RoundingMode.HALF_UP);
    }

    public java.util.List<String> knownPlaces() {
        return PLACES.keySet().stream().sorted().toList();
    }

    private double[] lookup(String name) {
        return name == null ? null : PLACES.get(name.trim().toLowerCase(Locale.ROOT));
    }

    public enum Source {
        PROVIDED,
        COORDINATES,
        PLACE_TABLE
    }

    public record ResolvedDistance(BigDecimal km, Source source) {
    }

    public static class DistanceUnresolvedException extends RuntimeException {
        public DistanceUnresolvedException(String message) {
            super(message);
        }
    }
}
