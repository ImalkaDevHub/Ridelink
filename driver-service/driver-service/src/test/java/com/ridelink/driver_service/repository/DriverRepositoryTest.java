package com.ridelink.driver_service.repository;

import com.ridelink.driver_service.model.Driver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.mongodb.test.autoconfigure.DataMongoTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// @DataMongoTest runs findByAvailableTrue() against a real (in-memory H2)
// database instead of a mock, because the actual filtering logic lives in
// Spring Data's derived query, not in any of our own code - a mocked
// repository test would only prove DriverService calls this method, not
// that it filters correctly. This is exactly the query ride-service's
// DriverClient depends on when picking a driver to assign: if it ever
// returned an unavailable driver, ride-service would try to reserve
// someone already on a trip.
//
// There is no other eligibility/filtering logic in this service today
// (e.g. no service-area matching) - if that's added later, extend this
// test class alongside it.
@DataMongoTest
class DriverRepositoryTest {

    @Autowired
    private DriverRepository driverRepository;

    @BeforeEach
    void cleanUp() {
        driverRepository.deleteAll();
    }

    @Test
    void findByAvailableTrue_returnsOnlyDriversMarkedAvailable() {
        driverRepository.save(newDriver("Nimal", true));
        driverRepository.save(newDriver("Sunil", true));
        driverRepository.save(newDriver("Kasun", false));

        List<Driver> result = driverRepository.findByAvailableTrue();

        assertThat(result)
                .extracting(Driver::getName)
                .containsExactlyInAnyOrder("Nimal", "Sunil");
    }

    @Test
    void findByAvailableTrue_returnsEmptyListWhenEveryDriverIsUnavailable() {
        driverRepository.save(newDriver("Kasun", false));

        List<Driver> result = driverRepository.findByAvailableTrue();

        assertThat(result).isEmpty();
    }

    private Driver newDriver(String name, boolean available) {
        Driver driver = new Driver();
        driver.setName(name);
        driver.setVehicleNumber("CAB-" + name);
        driver.setVehicleType("Sedan");
        driver.setServiceArea("Malabe");
        driver.setAvailable(available);
        return driver;
    }
}
