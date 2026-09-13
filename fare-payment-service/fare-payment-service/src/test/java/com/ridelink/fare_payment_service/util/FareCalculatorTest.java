package com.ridelink.fare_payment_service.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Verifies the documented formula:
// finalFare = baseFare + (perKm * distanceKm) + (perMin * durationMin), rounded to 2dp.
class FareCalculatorTest {

    private final FareCalculator calculator = new FareCalculator(100.0, 50.0, 10.0);

    @ParameterizedTest
    @CsvSource({
            "5.0, 12.0, 470.0",   // 100 + 50*5 + 10*12 = 100 + 250 + 120
            "1.0, 1.0, 160.0",    // 100 + 50 + 10
            "0.5, 2.5, 150.0",    // 100 + 25 + 25
    })
    void calculatesFareUsingTheDocumentedFormula(double distanceKm, double durationMin, double expectedFare) {
        assertThat(calculator.calculate(distanceKm, durationMin)).isEqualTo(expectedFare);
    }

    @Test
    void roundsToTwoDecimalPlaces() {
        FareCalculator oddRates = new FareCalculator(10.111, 3.333, 1.111);

        double fare = oddRates.calculate(2.0, 3.0);

        // 10.111 + 3.333*2 + 1.111*3 = 10.111 + 6.666 + 3.333 = 20.11 (rounded)
        assertThat(fare).isEqualTo(20.11);
    }

    @Test
    void rejectsNonPositiveDistance() {
        assertThatThrownBy(() -> calculator.calculate(0.0, 5.0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> calculator.calculate(-1.0, 5.0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNonPositiveDuration() {
        assertThatThrownBy(() -> calculator.calculate(5.0, 0.0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> calculator.calculate(5.0, -1.0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void breakdownStatesEachTermAndTheTotal() {
        String breakdown = calculator.describeBreakdown(5.0, 12.0);

        assertThat(breakdown)
                .contains("100.00")   // base fare
                .contains("250.00")   // 50/km * 5km
                .contains("120.00")   // 10/min * 12min
                .contains("470.00");  // total
    }
}
