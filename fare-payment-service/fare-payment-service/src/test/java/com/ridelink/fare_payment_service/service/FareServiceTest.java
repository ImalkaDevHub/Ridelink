package com.ridelink.fare_payment_service.service;

import com.ridelink.fare_payment_service.config.FareProperties;
import com.ridelink.fare_payment_service.dto.FareEstimateRequest;
import com.ridelink.fare_payment_service.model.FareEstimate;
import com.ridelink.fare_payment_service.model.FareRecord;
import com.ridelink.fare_payment_service.repository.FareEstimateRepository;
import com.ridelink.fare_payment_service.repository.FareRecordRepository;
import com.ridelink.fare_payment_service.util.DistanceResolver;
import com.ridelink.fare_payment_service.util.FareCalculator;
import com.ridelink.fare_payment_service.util.FareCalculatorTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FareServiceTest {

    @Mock
    private FareEstimateRepository fareEstimateRepository;

    @Mock
    private FareRecordRepository fareRecordRepository;

    @Spy
    private FareProperties fareProperties = FareCalculatorTest.defaultProperties();

    @Spy
    private FareCalculator fareCalculator = new FareCalculator(FareCalculatorTest.defaultProperties());

    @Spy
    private DistanceResolver distanceResolver = new DistanceResolver();

    @InjectMocks
    private FareService fareService;

    private FareEstimateRequest estimateRequest() {
        FareEstimateRequest request = new FareEstimateRequest();
        request.setPickupLocation("SLIIT");
        request.setDestinationLocation("Malabe Junction");
        request.setVehicleType("Sedan");
        return request;
    }

    // --- estimate ---------------------------------------------------------

    @Test
    void estimateWithDistanceAndDurationKeepsTheOriginalContract() {
        FareEstimateRequest request = estimateRequest();
        request.setDistanceKm(5.0);
        request.setDurationMin(12.0);

        var response = fareService.estimate(request);

        assertThat(response.estimatedFare()).isEqualByComparingTo("1020.00");
        assertThat(response.baseFare()).isEqualByComparingTo("300");
        assertThat(response.perKmRate()).isEqualByComparingTo("120");
        assertThat(response.perMinRate()).isEqualByComparingTo("10");
        assertThat(response.distanceSource()).isEqualTo("PROVIDED");
        assertThat(response.durationEstimated()).isFalse();
        assertThat(response.currency()).isEqualTo("LKR");

        ArgumentCaptor<FareEstimate> saved = ArgumentCaptor.forClass(FareEstimate.class);
        verify(fareEstimateRepository).save(saved.capture());
        assertThat(saved.getValue().getEstimatedFare()).isEqualByComparingTo("1020.00");
        assertThat(saved.getValue().getVehicleType()).isEqualTo("SEDAN");
    }

    @Test
    void estimateWithoutDistanceUsesThePlaceTableAndEstimatesDuration() {
        var response = fareService.estimate(estimateRequest());

        assertThat(response.distanceSource()).isEqualTo("PLACE_TABLE");
        assertThat(response.durationEstimated()).isTrue();
        // duration = km / 25 km/h * 60
        BigDecimal expected = response.distanceKm().multiply(new BigDecimal("60"))
                .divide(new BigDecimal("25"), 2, java.math.RoundingMode.HALF_UP);
        assertThat(response.durationMin()).isEqualByComparingTo(expected);
        assertThat(response.minimumApplied()).isTrue(); // ~1 km trip
        assertThat(response.estimatedFare()).isEqualByComparingTo("500.00");
    }

    @Test
    void estimateWithUnknownVehicleTypeIsRejectedAndNotStored() {
        FareEstimateRequest request = estimateRequest();
        request.setDistanceKm(5.0);
        request.setVehicleType("Truck");

        assertThatThrownBy(() -> fareService.estimate(request))
                .isInstanceOf(FareCalculator.InvalidVehicleTypeException.class);

        verify(fareEstimateRepository, never()).save(any());
    }

    // --- final fare -------------------------------------------------------

    @Test
    void finalizeFareStoresARecordWithTheBreakdown() {
        when(fareRecordRepository.findByRideId("ride-1")).thenReturn(Optional.empty());
        when(fareRecordRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var result = fareService.finalizeFare("ride-1", null, new BigDecimal("5"), new BigDecimal("12"), "van");

        assertThat(result.created()).isTrue();
        FareRecord record = result.record();
        assertThat(record.getRideId()).isEqualTo("ride-1");
        assertThat(record.getTotalFare()).isEqualByComparingTo("1530.00");
        assertThat(record.getMultiplier()).isEqualByComparingTo("1.5");
        assertThat(record.getVehicleType()).isEqualTo("VAN");
        assertThat(record.getBreakdown()).endsWith("= 1530.00 LKR");
    }

    @Test
    void finalizeFareIsIdempotentPerRide() {
        FareRecord existing = new FareRecord();
        existing.setRideId("ride-1");
        existing.setTotalFare(new BigDecimal("1020.00"));
        when(fareRecordRepository.findByRideId("ride-1")).thenReturn(Optional.of(existing));

        // Different inputs on the retry must not change the stored fare.
        var result = fareService.finalizeFare("ride-1", null, new BigDecimal("50"), new BigDecimal("90"), "VAN");

        assertThat(result.created()).isFalse();
        assertThat(result.record()).isSameAs(existing);
        verify(fareRecordRepository, never()).save(any());
    }

    @Test
    void finalizeFareRaceReturnsTheRecordThatWon() {
        FareRecord winner = new FareRecord();
        winner.setRideId("ride-1");
        when(fareRecordRepository.findByRideId("ride-1"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(winner));
        when(fareRecordRepository.save(any())).thenThrow(new DuplicateKeyException("E11000 rideId"));

        var result = fareService.finalizeFare("ride-1", null, new BigDecimal("5"), new BigDecimal("12"), "SEDAN");

        assertThat(result.created()).isFalse();
        assertThat(result.record()).isSameAs(winner);
    }

    @Test
    void lenientFinalizeAcceptsAnUnknownVehicleType() {
        when(fareRecordRepository.findByRideId("ride-1")).thenReturn(Optional.empty());
        when(fareRecordRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var result = fareService.finalizeFareLenient("ride-1", "p-1", new BigDecimal("5"), new BigDecimal("12"), "Truck");

        assertThat(result.record().getTotalFare()).isEqualByComparingTo("1020.00");
        assertThat(result.record().getPassengerId()).isEqualTo("p-1");
    @Test
    void findFareRecordReturnsOptionalRecord() {
        FareRecord record = new FareRecord();
        when(fareRecordRepository.findByRideId("ride-1")).thenReturn(Optional.of(record));

        var result = fareService.findFareRecord("ride-1");

        assertThat(result).isPresent().contains(record);
    }
}
