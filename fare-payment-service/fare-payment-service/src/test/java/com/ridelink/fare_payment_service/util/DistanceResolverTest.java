package com.ridelink.fare_payment_service.util;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class DistanceResolverTest {

    private final DistanceResolver resolver = new DistanceResolver();

    @Test
    void providedDistanceWinsOverEverythingElse() {
        DistanceResolver.ResolvedDistance result = resolver.resolve(new BigDecimal("7.5"),
                6.9, 79.9, 7.0, 80.0, "SLIIT", "Kandy");

        assertThat(result.km()).isEqualByComparingTo("7.5");
        assertThat(result.source()).isEqualTo(DistanceResolver.Source.PROVIDED);
    }

    @Test
    void coordinatesUseHaversine() {
        // One degree of latitude is ~111.2 km.
        DistanceResolver.ResolvedDistance result = resolver.resolve(null, 6.0, 80.0, 7.0, 80.0, null, null);

        assertThat(result.source()).isEqualTo(DistanceResolver.Source.COORDINATES);
        assertThat(result.km().doubleValue()).isCloseTo(111.19, within(0.05));
    }

    @Test
    void samePointIsZeroKm() {
        assertThat(resolver.haversineKm(6.9, 79.9, 6.9, 79.9)).isEqualByComparingTo("0");
    }

    @Test
    void knownPlaceNamesAreLookedUpCaseInsensitively() {
        DistanceResolver.ResolvedDistance result = resolver.resolve(null, null, null, null, null,
                "  sliit ", "Malabe Junction");

        assertThat(result.source()).isEqualTo(DistanceResolver.Source.PLACE_TABLE);
        assertThat(result.km().doubleValue()).isBetween(0.5, 2.0);
    }

    @Test
    void colomboFortToKandyIsAboutNinetyKmStraightLine() {
        DistanceResolver.ResolvedDistance result = resolver.resolve(null, null, null, null, null,
                "Colombo Fort", "Kandy");

        assertThat(result.km().doubleValue()).isBetween(85.0, 100.0);
    }

    @Test
    void partialCoordinatesFallBackToPlaceNames() {
        DistanceResolver.ResolvedDistance result = resolver.resolve(null, 6.9, null, null, null, "SLIIT", "Kaduwela");

        assertThat(result.source()).isEqualTo(DistanceResolver.Source.PLACE_TABLE);
    }

    @Test
    void unknownPlacesWithoutDistanceOrCoordinatesFail() {
        assertThatThrownBy(() -> resolver.resolve(null, null, null, null, null, "Atlantis", "SLIIT"))
                .isInstanceOf(DistanceResolver.DistanceUnresolvedException.class)
                .hasMessageContaining("sliit");
    }
}
