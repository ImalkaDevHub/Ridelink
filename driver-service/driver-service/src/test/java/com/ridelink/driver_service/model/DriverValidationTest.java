package com.ridelink.driver_service.model;

import com.ridelink.driver_service.dto.LocationUpdateRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

// Boundary tests for the bean-validation rules on Driver and the location DTO.
class DriverValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    private Driver valid() {
        Driver driver = new Driver();
        driver.setName("Nimal");
        driver.setEmail("nimal@example.com");
        driver.setVehicleNumber("CAB-1024");
        driver.setVehicleType("Sedan");
        driver.setServiceArea("Malabe");
        return driver;
    }

    private Set<String> invalidFields(Object target) {
        return validator.validate(target).stream()
                .map(v -> v.getPropertyPath().toString())
                .collect(Collectors.toSet());
    }

    @Test
    void validDriverHasNoViolations() {
        assertThat(invalidFields(valid())).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"SEDAN", "hatchback", "Van", "tuk", "Car", "BIKE"})
    void supportedVehicleTypesAreAcceptedInAnyCase(String type) {
        Driver driver = valid();
        driver.setVehicleType(type);

        assertThat(invalidFields(driver)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"Truck", "sedan2", "", " "})
    void unsupportedVehicleTypesAreRejected(String type) {
        Driver driver = valid();
        driver.setVehicleType(type);

        assertThat(invalidFields(driver)).containsExactly("vehicleType");
    }

    @ParameterizedTest
    @ValueSource(strings = {"CAB-1024", "WP CAB-1234", "300-1234", "BAC - 1099", "KQ0513"})
    void realisticVehicleNumbersAreAccepted(String number) {
        Driver driver = valid();
        driver.setVehicleNumber(number);

        assertThat(invalidFields(driver)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"ABC-XYZ", "CAB_1024", "-1234", "CAB--", "A", "ABCDEFGH-12345678"})
    void malformedVehicleNumbersAreRejected(String number) {
        Driver driver = valid();
        driver.setVehicleNumber(number);

        assertThat(invalidFields(driver)).containsExactly("vehicleNumber");
    }

    @Test
    void coordinateBoundaries() {
        Driver driver = valid();
        driver.setCurrentLat(90.0);
        driver.setCurrentLng(-180.0);
        assertThat(invalidFields(driver)).isEmpty();

        driver.setCurrentLat(90.0001);
        driver.setCurrentLng(-180.0001);
        assertThat(invalidFields(driver)).containsExactlyInAnyOrder("currentLat", "currentLng");
    }

    @Test
    void locationRequestRequiresBothCoordinatesInRange() {
        LocationUpdateRequest request = new LocationUpdateRequest();
        assertThat(invalidFields(request)).containsExactlyInAnyOrder("lat", "lng");

        request.setLat(-91.0);
        request.setLng(181.0);
        assertThat(invalidFields(request)).containsExactlyInAnyOrder("lat", "lng");

        request.setLat(6.9061);
        request.setLng(79.9696);
        assertThat(invalidFields(request)).isEmpty();
    }

    @Test
    void seatsMustBeBetweenOneAndTwenty() {
        Driver driver = valid();
        driver.setSeats(0);
        assertThat(invalidFields(driver)).containsExactly("seats");
        driver.setSeats(21);
        assertThat(invalidFields(driver)).containsExactly("seats");
        driver.setSeats(4);
        assertThat(invalidFields(driver)).isEmpty();
    }
}
