package com.ridelink.driver_service.service;

import com.ridelink.driver_service.model.Driver;
import com.ridelink.driver_service.repository.DriverRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Unit tests for DriverService's own logic, with DriverRepository mocked
// out. The actual "only available drivers come back" filtering behaviour
// lives in the repository's query method, not here - that's covered
// separately by DriverRepositoryTest against a real (in-memory) database,
// since mocking the repository here would only prove DriverService calls
// it, not that the query itself is correct.
@ExtendWith(MockitoExtension.class)
class DriverServiceTest {

    @Mock
    private DriverRepository driverRepository;

    // Only used by findEligible/reserve/release - see DriverServiceAtomicTest.
    @Mock
    private org.springframework.data.mongodb.core.MongoTemplate mongoTemplate;

    @InjectMocks
    private DriverService driverService;

    @Test
    void createDriver_savesAndReturnsTheNewDriver() {
        Driver driver = new Driver();
        driver.setName("Nimal");
        driver.setVehicleNumber("CAB-1024");
        driver.setVehicleType("Sedan");
        driver.setServiceArea("Malabe");
        when(driverRepository.save(driver)).thenReturn(driver);

        Driver saved = driverService.createDriver(driver);

        assertThat(saved).isSameAs(driver);
        verify(driverRepository).save(driver);
    }

    @Test
    void setAvailability_whenDriverIdDoesNotExist_throwsDriverNotFoundException() {
        // Protects the 404 DRIVER_NOT_FOUND behaviour in DriverController -
        // before this was fixed, a bad id here caused an unguarded 500.
        when(driverRepository.findById("99")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> driverService.setAvailability("99", true))
                .isInstanceOf(DriverService.DriverNotFoundException.class);

        verify(driverRepository, never()).save(any());
    }

    @Test
    void setAvailability_whenDriverExists_flipsTheAvailableFlagAndSaves() {
        Driver driver = new Driver();
        driver.setId("1");
        driver.setAvailable(true);
        when(driverRepository.findById("1")).thenReturn(Optional.of(driver));
        when(driverRepository.save(driver)).thenReturn(driver);

        Driver updated = driverService.setAvailability("1", false);

        assertThat(updated.isAvailable()).isFalse();
        verify(driverRepository).save(driver);
    }

    @Test
    void getAvailableDrivers_returnsExactlyWhatTheRepositoryReports() {
        Driver available = new Driver();
        available.setName("Sunil");
        available.setAvailable(true);
        when(driverRepository.findByAvailableTrue()).thenReturn(List.of(available));

        List<Driver> result = driverService.getAvailableDrivers();

        assertThat(result).containsExactly(available);
    }

    @Test
    void getAllDrivers_returnsAllDrivers() {
        Driver d1 = new Driver();
        Driver d2 = new Driver();
        when(driverRepository.findAll()).thenReturn(List.of(d1, d2));

        List<Driver> result = driverService.getAllDrivers();

        assertThat(result).containsExactly(d1, d2);
    }

    @Test
    void getAvailableDriversByType_returnsMatchingDrivers() {
        Driver d1 = new Driver();
        when(driverRepository.findByAvailableTrueAndVehicleTypeIgnoreCase("sedan")).thenReturn(List.of(d1));

        List<Driver> result = driverService.getAvailableDriversByType("sedan");

        assertThat(result).containsExactly(d1);
    }

    @Test
    void getDriverByAccountId_whenExists_returnsDriver() {
        Driver driver = new Driver();
        driver.setAccountId("acc-1");
        when(driverRepository.findByAccountId("acc-1")).thenReturn(Optional.of(driver));

        Driver result = driverService.getDriverByAccountId("acc-1");

        assertThat(result).isSameAs(driver);
    }

    @Test
    void getDriverByAccountId_whenDoesNotExist_throwsDriverNotFoundException() {
        when(driverRepository.findByAccountId("acc-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> driverService.getDriverByAccountId("acc-1"))
                .isInstanceOf(DriverService.DriverNotFoundException.class);
    }

    @Test
    void updateServiceArea_updatesAndSavesDriver() {
        Driver driver = new Driver();
        driver.setId("1");
        driver.setServiceArea("Colombo");
        when(driverRepository.findById("1")).thenReturn(Optional.of(driver));
        when(driverRepository.save(driver)).thenReturn(driver);

        Driver updated = driverService.updateServiceArea("1", " Malabe ");

        assertThat(updated.getServiceArea()).isEqualTo("Malabe");
        verify(driverRepository).save(driver);
    }
}
