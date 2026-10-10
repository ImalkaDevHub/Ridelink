package com.ridelink.fare_payment_service.util;

import com.ridelink.fare_payment_service.config.FareProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Verifies the documented rule with the default rates:
//   fare = max(500, 300 + 120 * km + 10 * min) * multiplier[vehicleType]
public class FareCalculatorTest {

    // Shared with FareServiceTest: the default rates from application.properties.
    public static FareProperties defaultProperties() {
        FareProperties properties = new FareProperties();
        properties.setBaseFare(new BigDecimal("300"));
        properties.setPerKm(new BigDecimal("120"));
        properties.setPerMin(new BigDecimal("10"));
        properties.setMinimumFare(new BigDecimal("500"));
        properties.setCurrency("LKR");
        Map<String, BigDecimal> multipliers = new LinkedHashMap<>();
        multipliers.put("sedan", new BigDecimal("1.0"));
        multipliers.put("hatchback", new BigDecimal("0.9"));
        multipliers.put("van", new BigDecimal("1.5"));
        multipliers.put("tuk", new BigDecimal("0.8"));
        multipliers.put("car", new BigDecimal("1.0"));
        multipliers.put("bike", new BigDecimal("0.6"));
        properties.setMultipliers(multipliers);
        return properties;
    }

    private final FareCalculator calculator = new FareCalculator(defaultProperties());

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }

    @Test
    void normalFareAppliesBasePlusDistancePlusTime() {
        // 300 + 120*5 + 10*12 = 1020
        FareBreakdown fare = calculator.calculate(bd("5"), bd("12"), "SEDAN");

        assertThat(fare.total()).isEqualByComparingTo("1020.00");
        assertThat(fare.subtotal()).isEqualByComparingTo("1020.00");
        assertThat(fare.distanceCharge()).isEqualByComparingTo("600.00");
        assertThat(fare.timeCharge()).isEqualByComparingTo("120.00");
        assertThat(fare.minimumApplied()).isFalse();
        assertThat(fare.currency()).isEqualTo("LKR");
        assertThat(fare.total().scale()).isEqualTo(2);
    }

    @Test
    void shortTripIsRaisedToTheMinimumFare() {
        // 300 + 120*1 + 10*3 = 450 < 500
        FareBreakdown fare = calculator.calculate(bd("1"), bd("3"), "SEDAN");

        assertThat(fare.subtotal()).isEqualByComparingTo("450.00");
        assertThat(fare.minimumApplied()).isTrue();
        assertThat(fare.total()).isEqualByComparingTo("500.00");
    }

    @Test
    void minimumBoundaryIsNotApplied() {
        // 300 + 120*1.5 + 10*2 = 500 exactly - not below the minimum
        FareBreakdown fare = calculator.calculate(bd("1.5"), bd("2"), "SEDAN");

        assertThat(fare.minimumApplied()).isFalse();
        assertThat(fare.total()).isEqualByComparingTo("500.00");
    }

    @Test
    void multiplierAppliesAfterTheMinimum() {
        // max(500, 450) * 0.8 = 400 - a short TUK ride costs less than the minimum
        assertThat(calculator.calculate(bd("1"), bd("3"), "TUK").total()).isEqualByComparingTo("400.00");
    }

    @ParameterizedTest
    @CsvSource({
            "SEDAN, 1020.00",      // 1020 * 1.0
            "HATCHBACK, 918.00",   // 1020 * 0.9
            "VAN, 1530.00",        // 1020 * 1.5
            "TUK, 816.00",         // 1020 * 0.8
            "CAR, 1020.00",        // legacy alias, 1.0
            "BIKE, 612.00",        // legacy alias, 0.6
            "sedan, 1020.00",      // case-insensitive
            "Van, 1530.00",
    })
    void eachVehicleTypeUsesItsMultiplier(String vehicleType, String expected) {
        FareBreakdown fare = calculator.calculate(bd("5"), bd("12"), vehicleType);

        assertThat(fare.total()).isEqualByComparingTo(expected);
        assertThat(fare.vehicleType()).isEqualTo(vehicleType.toUpperCase());
    }

    @Test
    void roundsHalfUpToTwoDecimalPlaces() {
        // 300 + 120*2.333 + 10*0 = 579.96; * 0.9 = 521.964 -> 521.96
        assertThat(calculator.calculate(bd("2.333"), bd("0"), "HATCHBACK").total()).isEqualByComparingTo("521.96");
        // 300 + 120*2.3335 = 580.02; * 0.9 = 522.018 -> 522.02
        assertThat(calculator.calculate(bd("2.3335"), bd("0"), "HATCHBACK").total()).isEqualByComparingTo("522.02");
    }

    @Test
    void moneyIsExactWithNoBinaryFloatingPointDrift() {
        // 0.1 km steps would drift with doubles; BigDecimal stays exact.
        // 300 + 120*3.3 + 10*0.1 = 697.00
        assertThat(calculator.calculate(bd("3.3"), bd("0.1"), "SEDAN").total()).isEqualByComparingTo("697.00");
    }

    @Test
    void unknownVehicleTypeIsRejected() {
        assertThatThrownBy(() -> calculator.calculate(bd("5"), bd("12"), "Truck"))
                .isInstanceOf(FareCalculator.InvalidVehicleTypeException.class)
                .hasMessageContaining("SEDAN");
    }

    @Test
    void lenientCalculationUsesMultiplierOneForUnknownTypes() {
        assertThat(calculator.calculateLenient(bd("5"), bd("12"), "Truck").total()).isEqualByComparingTo("1020.00");
        assertThat(calculator.calculateLenient(bd("5"), bd("12"), "VAN").total()).isEqualByComparingTo("1530.00");
    }

    @Test
    void rejectsNonPositiveDistanceAndNegativeDuration() {
        assertThatThrownBy(() -> calculator.calculate(bd("0"), bd("5"), "SEDAN"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> calculator.calculate(bd("-1"), bd("5"), "SEDAN"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> calculator.calculate(bd("5"), bd("-1"), "SEDAN"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void zeroDurationIsAllowed() {
        assertThat(calculator.calculate(bd("5"), bd("0"), "SEDAN").total()).isEqualByComparingTo("900.00");
    }

    @Test
    void breakdownStatesEachTermAndTheTotal() {
        String text = calculator.calculate(bd("5"), bd("12"), "VAN").describe();

        assertThat(text)
                .contains("500.00")    // minimum
                .contains("300.00")    // base
                .contains("120.00/km x 5.00 km")
                .contains("10.00/min x 12.00 min")
                .contains("1020.00")   // subtotal
                .contains("x 1.5 (VAN)")
                .endsWith("= 1530.00 LKR");
    }
}
