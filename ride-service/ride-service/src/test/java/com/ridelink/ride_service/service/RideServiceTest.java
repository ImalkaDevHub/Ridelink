package com.ridelink.ride_service.service;

import com.ridelink.ride_service.client.DriverClient;
import com.ridelink.ride_service.client.FareClient;
import com.ridelink.ride_service.dto.DriverResponse;
import com.ridelink.ride_service.dto.FareEstimateRequest;
import com.ridelink.ride_service.dto.FinalizeFareRequest;
import com.ridelink.ride_service.model.Ride;
import com.ridelink.ride_service.model.RideStatus;
import com.ridelink.ride_service.repository.RideRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// RideService with both clients and the repository mocked - no MongoDB,
// no other services.
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RideServiceTest {

    private static final String TOKEN = "Bearer test-token";

    @Mock
    private RideRepository rideRepository;

    @Mock
    private DriverClient driverClient;

    @Mock
    private FareClient fareClient;

    @Mock
    private HttpServletRequest request;

    @InjectMocks
    private RideService rideService;

    @BeforeEach
    void setUp() {
        authenticateAs("passenger-1", "PASSENGER");
        when(request.getHeader("Authorization")).thenReturn(TOKEN);
        when(rideRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        ReflectionTestUtils.setField(rideService, "autoChargeOnComplete", true);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(String accountId, String role) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(
                accountId, null, List.of(new SimpleGrantedAuthority(role))));
        SecurityContextHolder.setContext(context);
    }

    private Ride newRequest() {
        Ride ride = new Ride();
        ride.setPassengerName("Pat");
        ride.setPickup("SLIIT");
        ride.setDestination("Kaduwela");
        ride.setVehicleType("Car");
        ride.setDistance(5.0);
        ride.setDuration(12);
        return ride;
    }

    private Ride storedRide(RideStatus status) {
        Ride ride = new Ride();
        ride.setId("ride-1");
        ride.setPassengerId("passenger-1");
        ride.setDriverId("driver-7");
        ride.setDriverAccountId("driver-acc-7");
        ride.setVehicleType("Car");
        ride.setStatus(status);
        when(rideRepository.findById("ride-1")).thenReturn(Optional.of(ride));
        return ride;
    }

    private DriverResponse reservedDriver() {
        DriverResponse driver = new DriverResponse();
        driver.setId("driver-7");
        driver.setAccountId("driver-acc-7");
        return driver;
    }

    // --- request: reserve + estimate --------------------------------------

    @Test
    void requestRide_reservesADriverAtomicallyAndStoresDriverAndAccountIds() {
        when(driverClient.reserveDriver("Car", TOKEN)).thenReturn(reservedDriver());
        when(fareClient.estimateFare(any())).thenReturn(new BigDecimal("1020.00"));

        Ride result = rideService.requestRide(newRequest());

        assertThat(result.getStatus()).isEqualTo(RideStatus.ASSIGNED);
        assertThat(result.getDriverId()).isEqualTo("driver-7");
        assertThat(result.getDriverAccountId()).isEqualTo("driver-acc-7");
        assertThat(result.getPassengerId()).isEqualTo("passenger-1"); // from the JWT
        assertThat(result.getEstimatedFare()).isEqualByComparingTo("1020.00");
        assertThat(result.getRequestedAt()).isNotNull();

        ArgumentCaptor<FareEstimateRequest> estimate = ArgumentCaptor.forClass(FareEstimateRequest.class);
        verify(fareClient).estimateFare(estimate.capture());
        assertThat(estimate.getValue().distanceKm()).isEqualTo(5.0);   // legacy "distance" field
        assertThat(estimate.getValue().durationMin()).isEqualTo(12.0); // legacy "duration" field
    }

    @Test
    void requestRide_ignoresServerOwnedFieldsSentByTheClient() {
        when(driverClient.reserveDriver(any(), any())).thenReturn(reservedDriver());
        Ride sneaky = newRequest();
        sneaky.setId("chosen-id");
        sneaky.setStatus(RideStatus.COMPLETED);
        sneaky.setPassengerId("someone-else");
        sneaky.setFinalFare(BigDecimal.ONE);

        Ride result = rideService.requestRide(sneaky);

        assertThat(result.getId()).isNull();
        assertThat(result.getStatus()).isEqualTo(RideStatus.ASSIGNED);
        assertThat(result.getPassengerId()).isEqualTo("passenger-1");
        assertThat(result.getFinalFare()).isNull();
    }

    @Test
    void requestRide_whenNoDriverIsAvailable_throwsAndSavesNothing() {
        when(driverClient.reserveDriver(any(), any())).thenReturn(null);

        assertThatThrownBy(() -> rideService.requestRide(newRequest()))
                .isInstanceOf(RideService.NoDriverAvailableException.class);

        verify(rideRepository, never()).save(any());
        verifyNoInteractions(fareClient);
    }

    @Test
    void requestRide_whenDriverServiceIsDown_propagatesUnavailableAndSavesNothing() {
        when(driverClient.reserveDriver(any(), any()))
                .thenThrow(new DriverClient.DriverServiceUnavailableException("Driver service is unavailable"));

        assertThatThrownBy(() -> rideService.requestRide(newRequest()))
                .isInstanceOf(DriverClient.DriverServiceUnavailableException.class);

        verify(rideRepository, never()).save(any());
    }

    @Test
    void requestRide_whenFareServiceIsDown_stillCreatesTheRideWithNoEstimate() {
        when(driverClient.reserveDriver(any(), any())).thenReturn(reservedDriver());
        when(fareClient.estimateFare(any())).thenReturn(null); // FareClient swallows the failure

        Ride result = rideService.requestRide(newRequest());

        assertThat(result.getStatus()).isEqualTo(RideStatus.ASSIGNED);
        assertThat(result.getEstimatedFare()).isNull();
        verify(rideRepository).save(any());
    }

    @Test
    void requestRide_whenSavingFails_releasesTheReservedDriver() {
        when(driverClient.reserveDriver(any(), any())).thenReturn(reservedDriver());
        when(rideRepository.save(any())).thenThrow(new IllegalStateException("db down"));

        assertThatThrownBy(() -> rideService.requestRide(newRequest())).isInstanceOf(IllegalStateException.class);

        verify(driverClient).releaseDriver("driver-7", TOKEN);
    }

    // --- accept / start ---------------------------------------------------

    @Test
    void accept_byTheAssignedDriver_movesAssignedToAccepted() {
        authenticateAs("driver-acc-7", "DRIVER");
        storedRide(RideStatus.ASSIGNED);

        Ride result = rideService.acceptRide("ride-1");

        assertThat(result.getStatus()).isEqualTo(RideStatus.ACCEPTED);
        assertThat(result.getAcceptedAt()).isNotNull();
    }

    @Test
    void accept_byAnotherDriver_isForbidden() {
        authenticateAs("driver-acc-99", "DRIVER");
        storedRide(RideStatus.ASSIGNED);

        assertThatThrownBy(() -> rideService.acceptRide("ride-1"))
                .isInstanceOf(RideService.NotRideParticipantException.class);
        verify(rideRepository, never()).save(any());
    }

    @Test
    void accept_onALegacyRideWithoutDriverAccount_isAllowedForAnyDriver() {
        authenticateAs("driver-acc-99", "DRIVER");
        storedRide(RideStatus.ASSIGNED).setDriverAccountId(null);

        assertThat(rideService.acceptRide("ride-1").getStatus()).isEqualTo(RideStatus.ACCEPTED);
    }

    @ParameterizedTest
    @EnumSource(value = RideStatus.class, names = {"REQUESTED", "ACCEPTED", "IN_PROGRESS", "COMPLETED", "CANCELLED"})
    void accept_fromAnythingButAssigned_isAnInvalidTransition(RideStatus status) {
        authenticateAs("driver-acc-7", "DRIVER");
        storedRide(status);

        assertThatThrownBy(() -> rideService.acceptRide("ride-1"))
                .isInstanceOf(RideService.InvalidStatusTransitionException.class);
        verify(rideRepository, never()).save(any());
    }

    @Test
    void start_movesAcceptedToInProgress() {
        authenticateAs("driver-acc-7", "DRIVER");
        storedRide(RideStatus.ACCEPTED);

        Ride result = rideService.startRide("ride-1");

        assertThat(result.getStatus()).isEqualTo(RideStatus.IN_PROGRESS);
        assertThat(result.getStartedAt()).isNotNull();
    }

    @ParameterizedTest
    @EnumSource(value = RideStatus.class, names = {"REQUESTED", "ASSIGNED", "IN_PROGRESS", "COMPLETED", "CANCELLED"})
    void start_fromAnythingButAccepted_isAnInvalidTransition(RideStatus status) {
        authenticateAs("driver-acc-7", "DRIVER");
        storedRide(status);

        assertThatThrownBy(() -> rideService.startRide("ride-1"))
                .isInstanceOf(RideService.InvalidStatusTransitionException.class);
    }

    @Test
    void unknownRide_isNotFound() {
        authenticateAs("driver-acc-7", "DRIVER");
        when(rideRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> rideService.startRide("missing"))
                .isInstanceOf(RideService.RideNotFoundException.class);
    }

    // --- cancel -----------------------------------------------------------

    @ParameterizedTest
    @EnumSource(value = RideStatus.class, names = {"REQUESTED", "ASSIGNED", "ACCEPTED"})
    void cancel_beforeTheRideStarts_isAllowedAndReleasesTheDriver(RideStatus status) {
        storedRide(status);

        Ride result = rideService.cancelRide("ride-1", "  Changed my mind  ");

        assertThat(result.getStatus()).isEqualTo(RideStatus.CANCELLED);
        assertThat(result.getCancelReason()).isEqualTo("Changed my mind");
        assertThat(result.getCancelledAt()).isNotNull();
        verify(driverClient).releaseDriver("driver-7", TOKEN);
    }

    @ParameterizedTest
    @EnumSource(value = RideStatus.class, names = {"IN_PROGRESS", "COMPLETED", "CANCELLED"})
    void cancel_onceInProgressOrFinished_isAnInvalidTransition(RideStatus status) {
        storedRide(status);

        assertThatThrownBy(() -> rideService.cancelRide("ride-1", null))
                .isInstanceOf(RideService.InvalidStatusTransitionException.class);

        verify(rideRepository, never()).save(any());
        verify(driverClient, never()).releaseDriver(anyString(), any());
    }

    @Test
    void cancel_byTheAssignedDriver_isAllowed() {
        authenticateAs("driver-acc-7", "DRIVER");
        storedRide(RideStatus.ACCEPTED);

        assertThat(rideService.cancelRide("ride-1", null).getStatus()).isEqualTo(RideStatus.CANCELLED);
    }

    @Test
    void cancel_byAnotherPassenger_isForbidden() {
        authenticateAs("passenger-2", "PASSENGER");
        storedRide(RideStatus.ASSIGNED);

        assertThatThrownBy(() -> rideService.cancelRide("ride-1", null))
                .isInstanceOf(RideService.NotRideParticipantException.class);
    }

    @Test
    void cancel_whenDriverServiceIsDown_stillCancels() {
        // releaseDriver is best effort: DriverClient logs and returns false.
        storedRide(RideStatus.ASSIGNED);
        when(driverClient.releaseDriver(anyString(), any())).thenReturn(false);

        assertThat(rideService.cancelRide("ride-1", null).getStatus()).isEqualTo(RideStatus.CANCELLED);
    }

    // --- complete ---------------------------------------------------------

    @Test
    void complete_fromInProgress_storesFinalFareChargesAndReleasesTheDriver() {
        authenticateAs("driver-acc-7", "DRIVER");
        storedRide(RideStatus.IN_PROGRESS);
        when(fareClient.finalizeFare(any(), eq(TOKEN))).thenReturn(new BigDecimal("1020.00"));

        Ride result = rideService.completeRide("ride-1", 5.0, 12.0);

        assertThat(result.getStatus()).isEqualTo(RideStatus.COMPLETED);
        assertThat(result.getFinalFare()).isEqualByComparingTo("1020.00");
        assertThat(result.getDistanceKm()).isEqualTo(5.0);
        assertThat(result.getCompletedAt()).isNotNull();

        ArgumentCaptor<FinalizeFareRequest> fare = ArgumentCaptor.forClass(FinalizeFareRequest.class);
        verify(fareClient).finalizeFare(fare.capture(), eq(TOKEN));
        assertThat(fare.getValue().rideId()).isEqualTo("ride-1");
        assertThat(fare.getValue().passengerId()).isEqualTo("passenger-1");
        verify(fareClient).finalizePayment("ride-1", "passenger-1", 5.0, 12.0, "Car", TOKEN);
        verify(driverClient).releaseDriver("driver-7", TOKEN);
    }

    @Test
    void complete_legacyCallFromAssigned_walksAcceptedAndInProgressFirst() {
        // The original API completed straight from ASSIGNED.
        authenticateAs("driver-acc-7", "DRIVER");
        storedRide(RideStatus.ASSIGNED);
        when(fareClient.finalizeFare(any(), any())).thenReturn(new BigDecimal("1020.00"));

        Ride result = rideService.completeRide("ride-1", 5.0, 12.0);

        assertThat(result.getStatus()).isEqualTo(RideStatus.COMPLETED);
        assertThat(result.getAcceptedAt()).isNotNull();
        assertThat(result.getStartedAt()).isNotNull();
        assertThat(result.getCompletedAt()).isNotNull();
    }

    @Test
    void complete_fromAccepted_walksInProgressFirst() {
        authenticateAs("driver-acc-7", "DRIVER");
        storedRide(RideStatus.ACCEPTED);
        when(fareClient.finalizeFare(any(), any())).thenReturn(new BigDecimal("1020.00"));

        Ride result = rideService.completeRide("ride-1", 5.0, 12.0);

        assertThat(result.getStatus()).isEqualTo(RideStatus.COMPLETED);
        assertThat(result.getStartedAt()).isNotNull();
    }

    @ParameterizedTest
    @EnumSource(value = RideStatus.class, names = {"REQUESTED", "COMPLETED", "CANCELLED"})
    void complete_fromAnUncompletableStatus_failsBeforeAnyRemoteCall(RideStatus status) {
        authenticateAs("driver-acc-7", "DRIVER");
        storedRide(status);

        assertThatThrownBy(() -> rideService.completeRide("ride-1", 5.0, 12.0))
                .isInstanceOf(RideService.InvalidStatusTransitionException.class);

        verifyNoInteractions(fareClient);
        verify(rideRepository, never()).save(any());
    }

    @Test
    void complete_whenFareServiceIsDown_leavesTheRideInProgress() {
        authenticateAs("driver-acc-7", "DRIVER");
        Ride ride = storedRide(RideStatus.IN_PROGRESS);
        when(fareClient.finalizeFare(any(), any()))
                .thenThrow(new FareClient.FareServiceUnavailableException("Fare & payment service is unavailable"));

        assertThatThrownBy(() -> rideService.completeRide("ride-1", 5.0, 12.0))
                .isInstanceOf(FareClient.FareServiceUnavailableException.class);

        assertThat(ride.getStatus()).isEqualTo(RideStatus.IN_PROGRESS);
        assertThat(ride.getFinalFare()).isNull();
        verify(rideRepository, never()).save(any());
        verify(driverClient, never()).releaseDriver(anyString(), any());
    }

    @Test
    void complete_whenPaymentRecordingFails_leavesTheRideUnchanged() {
        authenticateAs("driver-acc-7", "DRIVER");
        Ride ride = storedRide(RideStatus.IN_PROGRESS);
        when(fareClient.finalizeFare(any(), any())).thenReturn(new BigDecimal("1020.00"));
        when(fareClient.finalizePayment(anyString(), any(), anyDouble(), anyDouble(), any(), any()))
                .thenThrow(new FareClient.PaymentServiceUnavailableException("Fare & payment service is unavailable"));

        assertThatThrownBy(() -> rideService.completeRide("ride-1", 5.0, 12.0))
                .isInstanceOf(RideService.PaymentFailedException.class);

        assertThat(ride.getStatus()).isEqualTo(RideStatus.IN_PROGRESS);
        verify(rideRepository, never()).save(any());
    }

    @Test
    void complete_whenTheRideWasAlreadyPaid_stillCompletes() {
        authenticateAs("driver-acc-7", "DRIVER");
        storedRide(RideStatus.IN_PROGRESS);
        when(fareClient.finalizeFare(any(), any())).thenReturn(new BigDecimal("1020.00"));
        when(fareClient.finalizePayment(anyString(), any(), anyDouble(), anyDouble(), any(), any()))
                .thenThrow(new FareClient.DuplicatePaymentException("A payment already exists for ride: ride-1"));

        assertThat(rideService.completeRide("ride-1", 5.0, 12.0).getStatus()).isEqualTo(RideStatus.COMPLETED);
    }

    @Test
    void complete_withAutoChargeOff_onlyFinalizesTheFare() {
        ReflectionTestUtils.setField(rideService, "autoChargeOnComplete", false);
        authenticateAs("driver-acc-7", "DRIVER");
        storedRide(RideStatus.IN_PROGRESS);
        when(fareClient.finalizeFare(any(), any())).thenReturn(new BigDecimal("1020.00"));

        rideService.completeRide("ride-1", 5.0, 12.0);

        verify(fareClient, never()).finalizePayment(anyString(), any(), anyDouble(), anyDouble(), any(), any());
    }

    @Test
    void complete_whenDriverServiceIsDownForTheRelease_stillCompletes() {
        authenticateAs("driver-acc-7", "DRIVER");
        storedRide(RideStatus.IN_PROGRESS);
        when(fareClient.finalizeFare(any(), any())).thenReturn(new BigDecimal("1020.00"));
        when(driverClient.releaseDriver(anyString(), any())).thenReturn(false);

        assertThat(rideService.completeRide("ride-1", 5.0, 12.0).getStatus()).isEqualTo(RideStatus.COMPLETED);
    }

    @Test
    void complete_legacyRideWithoutVehicleType_isPricedAsCar() {
        authenticateAs("driver-acc-7", "DRIVER");
        storedRide(RideStatus.ASSIGNED).setVehicleType(null);
        when(fareClient.finalizeFare(any(), any())).thenReturn(new BigDecimal("1020.00"));

        rideService.completeRide("ride-1", 5.0, 12.0);

        ArgumentCaptor<FinalizeFareRequest> fare = ArgumentCaptor.forClass(FinalizeFareRequest.class);
        verify(fareClient).finalizeFare(fare.capture(), any());
        assertThat(fare.getValue().vehicleType()).isEqualTo("CAR");
    }

    @Test
    void complete_byAnotherDriver_isForbidden() {
        authenticateAs("driver-acc-99", "DRIVER");
        storedRide(RideStatus.IN_PROGRESS);

        assertThatThrownBy(() -> rideService.completeRide("ride-1", 5.0, 12.0))
                .isInstanceOf(RideService.NotRideParticipantException.class);
        verifyNoInteractions(fareClient);
    }

    // --- reads ------------------------------------------------------------

    @Test
    void getRide_byAnotherPassenger_isForbidden() {
        authenticateAs("passenger-2", "PASSENGER");
        storedRide(RideStatus.ASSIGNED);

        assertThatThrownBy(() -> rideService.getRide("ride-1"))
                .isInstanceOf(RideService.NotRideParticipantException.class);
    }

    @Test
    void findRides_filtersByStatus() {
        Ride assigned = new Ride();
        assigned.setStatus(RideStatus.ASSIGNED);
        Ride done = new Ride();
        done.setStatus(RideStatus.COMPLETED);
        when(rideRepository.findByPassengerIdOrderByIdDesc("passenger-1")).thenReturn(List.of(assigned, done));

        assertThat(rideService.findRides("passenger-1", null, "completed")).containsExactly(done);
        assertThat(rideService.findRides(null, null, null)).containsExactly(assigned, done); // own rides
    }

    @Test
    void findRides_forAnotherPassenger_isForbidden() {
        assertThatThrownBy(() -> rideService.findRides("passenger-2", null, null))
                .isInstanceOf(RideService.NotRideParticipantException.class);
    }

    @Test
    void findRides_withUnknownStatus_isRejected() {
        assertThatThrownBy(() -> rideService.findRides("passenger-1", null, "FLYING"))
                .isInstanceOf(RideService.InvalidStatusFilterException.class);
    }
}
