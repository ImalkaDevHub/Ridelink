package com.ridelink.ride_service.service;

import com.ridelink.ride_service.client.DriverClient;
import com.ridelink.ride_service.client.FareClient;
import com.ridelink.ride_service.dto.DriverResponse;
import com.ridelink.ride_service.model.Ride;
import com.ridelink.ride_service.repository.RideRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RideServiceTest {

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

    // requestRide() reads the caller's account id off the SecurityContext
    // (ride creation always requires a token - see SecurityConfig), so
    // every test needs one populated, the same way a real authenticated
    // request would.
    @BeforeEach
    void setAuthenticatedCaller() {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken("42", null));
        SecurityContextHolder.setContext(context);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private Ride assignedRide() {
        Ride ride = new Ride();
        ride.setId(1L);
        ride.setPassengerId("42");
        ride.setStatus("ASSIGNED");
        return ride;
    }

    // --- requestRide ---------------------------------------------------

    @Test
    void requestRide_whenNoDriverIsAvailable_throwsNoDriverAvailableException() {
        // DriverClient itself returns null (not an exception) when the
        // available-drivers list is empty - this is the NO_DRIVER_AVAILABLE
        // path surfaced as a 409 by RideController.
        when(driverClient.findAndReserveAvailableDriver()).thenReturn(null);

        Ride requested = new Ride();
        assertThatThrownBy(() -> rideService.requestRide(requested))
                .isInstanceOf(RideService.NoDriverAvailableException.class);

        verify(rideRepository, never()).save(any());
    }

    @Test
    void requestRide_whenADriverIsAvailable_assignsTheDriverAndSetsStatusAssigned() {
        DriverResponse driver = new DriverResponse();
        driver.setId(7L);
        when(driverClient.findAndReserveAvailableDriver()).thenReturn(driver);
        when(rideRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Ride requested = new Ride();
        Ride result = rideService.requestRide(requested);

        assertThat(result.getDriverId()).isEqualTo(7L);
        assertThat(result.getStatus()).isEqualTo("ASSIGNED");
        assertThat(result.getPassengerId()).isEqualTo("42"); // from the authenticated caller
    }

    // --- completeRide: status transitions --------------------------------

    @Test
    void completeRide_whenRideIsAssigned_succeedsAndMarksCompleted() {
        Ride ride = assignedRide();
        when(rideRepository.findById(1L)).thenReturn(Optional.of(ride));
        when(rideRepository.save(ride)).thenReturn(ride);

        Ride result = rideService.completeRide(1L, 5.0, 12.0);

        assertThat(result.getStatus()).isEqualTo("COMPLETED");
        verify(fareClient).finalizePayment(eq("1"), eq("42"), eq(5.0), eq(12.0), any());
    }

    @Test
    void completeRide_whenRideIsAlreadyCompleted_throwsInvalidStatusTransitionException() {
        Ride ride = assignedRide();
        ride.setStatus("COMPLETED");
        when(rideRepository.findById(1L)).thenReturn(Optional.of(ride));

        assertThatThrownBy(() -> rideService.completeRide(1L, 5.0, 12.0))
                .isInstanceOf(RideService.InvalidStatusTransitionException.class);

        // Rejected before ever attempting to charge the passenger again.
        verify(fareClient, never()).finalizePayment(anyString(), anyString(), anyDouble(), anyDouble(), any());
        verify(rideRepository, never()).save(any());
    }

    // --- completeRide: fare-payment-service consistency guarantee -------
    //
    // These two protect the rule built and manually verified earlier: a
    // ride must never be saved as COMPLETED unless fare-payment-service
    // actually recorded the payment, so the ride and payment records can
    // never drift out of sync.

    @Test
    void completeRide_whenFarePaymentServiceIsUnreachable_rideIsNotSavedAndPaymentFailedExceptionPropagates() {
        Ride ride = assignedRide();
        when(rideRepository.findById(1L)).thenReturn(Optional.of(ride));
        when(fareClient.finalizePayment(anyString(), anyString(), anyDouble(), anyDouble(), any()))
                .thenThrow(new FareClient.PaymentServiceUnavailableException("Fare & payment service is unavailable"));

        assertThatThrownBy(() -> rideService.completeRide(1L, 5.0, 12.0))
                .isInstanceOf(RideService.PaymentFailedException.class);

        assertThat(ride.getStatus()).isEqualTo("ASSIGNED"); // left unchanged
        verify(rideRepository, never()).save(any());
    }

    @Test
    void completeRide_whenARideIsSomehowAlreadyPaid_gracefullySucceedsAndMarksCompleted() {
        Ride ride = assignedRide();
        when(rideRepository.findById(1L)).thenReturn(Optional.of(ride));
        
        // Simulating the race condition where getPaymentStatus initially returns null
        when(fareClient.getPaymentStatus(anyString(), any())).thenReturn(null);
        
        // But finalizePayment throws the conflict exception because another request just created it
        when(fareClient.finalizePayment(anyString(), anyString(), anyDouble(), anyDouble(), any()))
                .thenThrow(new FareClient.DuplicatePaymentException("A payment already exists for ride: 1"));
                
        // The service should gracefully swallow it and save as COMPLETED
        when(rideRepository.save(ride)).thenReturn(ride);

        Ride result = rideService.completeRide(1L, 5.0, 12.0);

        assertThat(result.getStatus()).isEqualTo("COMPLETED");
    }

    // Note: the @PreAuthorize("hasAuthority('DRIVER') or hasAuthority('ADMIN')")
    // check on RideController.completeRide is enforced by Spring Security's
    // method-security proxy around the controller bean - it isn't part of
    // RideService's own logic, so it can't be exercised by a plain
    // Mockito-based service test like this one without standing up a full
    // Spring Security context. That check is already covered at the
    // integration/Postman level (a PASSENGER token gets 403, a DRIVER token
    // gets 200 - see the earlier manual verification).
}
