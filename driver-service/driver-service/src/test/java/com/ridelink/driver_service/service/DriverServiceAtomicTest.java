package com.ridelink.driver_service.service;

import com.ridelink.driver_service.dto.DriverUpdateRequest;
import com.ridelink.driver_service.model.Driver;
import com.ridelink.driver_service.repository.DriverRepository;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.UpdateDefinition;

import java.util.Optional;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// Unit tests for the gap-fix behaviour in DriverService: lookups, vehicle
// rules, and the atomic reserve/release. Repository and MongoTemplate are
// mocked - no MongoDB needed.
@ExtendWith(MockitoExtension.class)
class DriverServiceAtomicTest {

    @Mock
    private DriverRepository driverRepository;

    @Mock
    private MongoTemplate mongoTemplate;

    @InjectMocks
    private DriverService driverService;

    private Driver driver(String id) {
        Driver driver = new Driver();
        driver.setId(id);
        driver.setAccountId("acc-" + id);
        driver.setName("Nimal");
        driver.setEmail("nimal@example.com");
        driver.setVehicleNumber("CAB-1024");
        driver.setVehicleType("SEDAN");
        driver.setServiceArea("Malabe");
        return driver;
    }

    // --- not found --------------------------------------------------------

    @Test
    void getDriver_whenIdDoesNotExist_throwsDriverNotFound() {
        when(driverRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> driverService.getDriver("missing"))
                .isInstanceOf(DriverService.DriverNotFoundException.class)
                .hasMessageContaining("missing");
    }

    @Test
    void updateLocation_whenIdDoesNotExist_throwsDriverNotFoundAndSavesNothing() {
        when(driverRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> driverService.updateLocation("missing", 6.9, 79.9))
                .isInstanceOf(DriverService.DriverNotFoundException.class);

        verify(driverRepository, never()).save(any());
    }

    // --- create: duplicate vehicle / vehicle type --------------------------

    @Test
    void createDriver_withExistingVehicleNumber_throwsDuplicateVehicle() {
        Driver incoming = driver(null);
        incoming.setVehicleNumber(" cab-1024 ");
        when(driverRepository.findByVehicleNumber("CAB-1024")).thenReturn(Optional.of(driver("1")));

        assertThatThrownBy(() -> driverService.createDriver(incoming))
                .isInstanceOf(DriverService.DuplicateVehicleException.class);

        verify(driverRepository, never()).save(any());
    }

    @Test
    void createDriver_whenUniqueIndexRejectsConcurrentDuplicate_throwsDuplicateVehicle() {
        when(driverRepository.findByVehicleNumber("CAB-1024")).thenReturn(Optional.empty());
        when(driverRepository.save(any())).thenThrow(new DuplicateKeyException(
                "E11000 duplicate key error collection: ridelink_driver.drivers index: vehicleNumber"));

        assertThatThrownBy(() -> driverService.createDriver(driver(null)))
                .isInstanceOf(DriverService.DuplicateVehicleException.class);
    }

    @Test
    void createDriver_normalisesVehicleTypeAndNumberAndIgnoresClientIdAndVersion() {
        Driver incoming = driver("client-chosen-id");
        incoming.setVersion(7L);
        incoming.setVehicleType("hatchback");
        incoming.setVehicleNumber("wp cab-1234");
        when(driverRepository.findByVehicleNumber("WP CAB-1234")).thenReturn(Optional.empty());
        when(driverRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Driver saved = driverService.createDriver(incoming);

        assertThat(saved.getId()).isNull();
        assertThat(saved.getVersion()).isNull();
        assertThat(saved.getVehicleType()).isEqualTo("HATCHBACK");
        assertThat(saved.getVehicleNumber()).isEqualTo("WP CAB-1234");
    }

    @Test
    void createDriver_withUnsupportedVehicleType_throwsInvalidVehicleType() {
        Driver incoming = driver(null);
        incoming.setVehicleType("Truck");

        assertThatThrownBy(() -> driverService.createDriver(incoming))
                .isInstanceOf(DriverService.InvalidVehicleTypeException.class);

        verify(driverRepository, never()).save(any());
    }

    @Test
    void reserve_withUnsupportedVehicleType_throwsBeforeTouchingTheDatabase() {
        assertThatThrownBy(() -> driverService.reserve("Malabe", "Spaceship"))
                .isInstanceOf(DriverService.InvalidVehicleTypeException.class);

        verifyNoInteractions(mongoTemplate);
    }

    @Test
    void updateDriver_toAVehicleNumberOwnedByAnotherDriver_throwsDuplicateVehicle() {
        when(driverRepository.findById("1")).thenReturn(Optional.of(driver("1")));
        when(driverRepository.findByVehicleNumber("KQ-0513")).thenReturn(Optional.of(driver("2")));
        DriverUpdateRequest request = new DriverUpdateRequest();
        request.setVehicleNumber("kq-0513");

        assertThatThrownBy(() -> driverService.updateDriver("1", request))
                .isInstanceOf(DriverService.DuplicateVehicleException.class);

        verify(driverRepository, never()).save(any());
    }

    @Test
    void updateDriver_keepingItsOwnVehicleNumber_appliesOnlyTheGivenFields() {
        Driver existing = driver("1");
        when(driverRepository.findById("1")).thenReturn(Optional.of(existing));
        when(driverRepository.findByVehicleNumber("CAB-1024")).thenReturn(Optional.of(existing));
        when(driverRepository.save(existing)).thenReturn(existing);
        DriverUpdateRequest request = new DriverUpdateRequest();
        request.setVehicleNumber("CAB-1024");
        request.setSeats(4);

        Driver updated = driverService.updateDriver("1", request);

        assertThat(updated.getSeats()).isEqualTo(4);
        assertThat(updated.getName()).isEqualTo("Nimal"); // untouched
    }

    // --- reserve ----------------------------------------------------------

    @Test
    void reserve_whenADriverMatches_returnsItFromASingleAtomicFindAndModify() {
        Driver reserved = driver("7");
        when(mongoTemplate.findAndModify(any(Query.class), any(UpdateDefinition.class),
                any(FindAndModifyOptions.class), eq(Driver.class))).thenReturn(reserved);

        Driver result = driverService.reserve("malabe", "sedan");

        assertThat(result).isSameAs(reserved);
        assertThat(result.getAccountId()).isEqualTo("acc-7");

        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<UpdateDefinition> update = ArgumentCaptor.forClass(UpdateDefinition.class);
        ArgumentCaptor<FindAndModifyOptions> options = ArgumentCaptor.forClass(FindAndModifyOptions.class);
        verify(mongoTemplate).findAndModify(query.capture(), update.capture(), options.capture(), eq(Driver.class));

        Document filter = query.getValue().getQueryObject();
        assertThat(filter.get("available")).isEqualTo(true);
        assertThat(((Pattern) filter.get("vehicleType")).matcher("Sedan").matches()).isTrue();
        assertThat(((Pattern) filter.get("serviceArea")).matcher("Malabe").matches()).isTrue();

        Document set = (Document) update.getValue().getUpdateObject().get("$set");
        assertThat(set.get("available")).isEqualTo(false);
        assertThat(options.getValue().isReturnNew()).isTrue();

        // Never read-then-write.
        verify(driverRepository, never()).findById(any());
        verify(driverRepository, never()).save(any());
    }

    @Test
    void reserve_withoutFilters_onlyRequiresAvailability() {
        when(mongoTemplate.findAndModify(any(Query.class), any(UpdateDefinition.class),
                any(FindAndModifyOptions.class), eq(Driver.class))).thenReturn(driver("7"));

        driverService.reserve(null, " ");

        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).findAndModify(query.capture(), any(UpdateDefinition.class),
                any(FindAndModifyOptions.class), eq(Driver.class));
        assertThat(query.getValue().getQueryObject().keySet()).containsExactly("available");
    }

    @Test
    void reserve_areaInputIsMatchedLiterallyNotAsARegex() {
        when(mongoTemplate.findAndModify(any(Query.class), any(UpdateDefinition.class),
                any(FindAndModifyOptions.class), eq(Driver.class))).thenReturn(driver("7"));

        driverService.reserve(".*", null);

        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).findAndModify(query.capture(), any(UpdateDefinition.class),
                any(FindAndModifyOptions.class), eq(Driver.class));
        Pattern area = (Pattern) query.getValue().getQueryObject().get("serviceArea");
        assertThat(area.matcher("Malabe").matches()).isFalse();
        assertThat(area.matcher(".*").matches()).isTrue();
    }

    @Test
    void reserve_whenNoDriverMatches_throwsNoDriverAvailable() {
        when(mongoTemplate.findAndModify(any(Query.class), any(UpdateDefinition.class),
                any(FindAndModifyOptions.class), eq(Driver.class))).thenReturn(null);

        assertThatThrownBy(() -> driverService.reserve("Malabe", "VAN"))
                .isInstanceOf(DriverService.NoDriverAvailableException.class);
    }

    // --- release ----------------------------------------------------------

    @Test
    void release_setsAvailableTrueAtomicallyAndReturnsTheDriver() {
        Driver released = driver("7");
        released.setAvailable(true);
        when(mongoTemplate.findAndModify(any(Query.class), any(UpdateDefinition.class),
                any(FindAndModifyOptions.class), eq(Driver.class))).thenReturn(released);

        Driver result = driverService.release("7");

        assertThat(result.isAvailable()).isTrue();
        ArgumentCaptor<UpdateDefinition> update = ArgumentCaptor.forClass(UpdateDefinition.class);
        verify(mongoTemplate).findAndModify(any(Query.class), update.capture(),
                any(FindAndModifyOptions.class), eq(Driver.class));
        Document set = (Document) update.getValue().getUpdateObject().get("$set");
        assertThat(set.get("available")).isEqualTo(true);
        verify(driverRepository, never()).save(any());
    }

    @Test
    void release_whenIdDoesNotExist_throwsDriverNotFound() {
        when(mongoTemplate.findAndModify(any(Query.class), any(UpdateDefinition.class),
                any(FindAndModifyOptions.class), eq(Driver.class))).thenReturn(null);

        assertThatThrownBy(() -> driverService.release("missing"))
                .isInstanceOf(DriverService.DriverNotFoundException.class);
    }
}
