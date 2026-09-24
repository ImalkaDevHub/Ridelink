package com.ridelink.ride_service.service;

import com.ridelink.ride_service.client.DriverClient;
import com.ridelink.ride_service.client.FareClient;
import com.ridelink.ride_service.dto.DriverResponse;
import com.ridelink.ride_service.model.Ride;
import com.ridelink.ride_service.repository.RideRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class RideService {

    @Autowired
    private RideRepository rideRepository;

    @Autowired
    private DriverClient driverClient;

    @Autowired
    private FareClient fareClient;

    // Spring injects a thread-bound proxy here even though this bean is a
    // singleton, so this always reflects the request currently being
    // handled - used to forward the caller's JWT to fare-payment-service.
    @Autowired
    private HttpServletRequest request;

    public Ride requestRide(Ride ride) {
        DriverResponse driver = driverClient.findAndReserveAvailableDriver();

        if (driver == null) {
            throw new NoDriverAvailableException("No driver is available right now.");
        }

        // Ride creation requires authentication (see SecurityConfig), so the
        // caller's account id is always available here - recorded as the
        // payer for when this ride is later completed and paid for.
        Object requester = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        ride.setPassengerId(requester != null ? requester.toString() : null);

        ride.setDriverId(driver.getId());
        ride.setStatus("ASSIGNED");
        return rideRepository.save(ride);
    }

    public Ride getRide(String id) {
        return rideRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Ride not found with id: " + id));
    }

    public java.util.List<Ride> getRidesByPassenger(String passengerId) {
        return rideRepository.findByPassengerIdOrderByIdDesc(passengerId);
    }

    public java.util.List<Ride> getRidesByDriver(String driverId) {
        return rideRepository.findByDriverIdOrderByIdDesc(driverId);
    }

    // Required interservice interaction between Ride Management and Fare &
    // Payment, per the architecture design: completing a ride must record a
    // real payment, so this makes a synchronous REST call to
    // fare-payment-service's POST /api/payments/finalize, forwarding the
    // original caller's JWT since that endpoint requires authentication.
    // The ride is only saved as COMPLETED if that call succeeds - if it
    // fails (network error, or the ride turns out to already be paid), the
    // ride is left in its prior status instead, so the ride and payment
    // records can never drift out of sync.
    public Ride completeRide(String id, double distanceKm, double durationMin) {
        Ride ride = getRide(id);

        if (!"ASSIGNED".equals(ride.getStatus()) && !"IN_PROGRESS".equals(ride.getStatus())) {
            throw new InvalidStatusTransitionException(
                    "Cannot complete a ride with status: " + ride.getStatus());
        }

        String authorizationHeader = request.getHeader("Authorization");

        try {
            // 1. Before attempting to create a new payment, check if a payment already exists.
            com.ridelink.ride_service.dto.PaymentResponse existingPayment = fareClient.getPaymentStatus(
                    ride.getId(), authorizationHeader);
            
            if (existingPayment == null) {
                // 2. No payment exists yet, finalize the payment.
                fareClient.finalizePayment(
                        ride.getId(), ride.getPassengerId(), distanceKm, durationMin, authorizationHeader);
            }
        } catch (FareClient.DuplicatePaymentException e) {
            // If it somehow still conflicts concurrently, gracefully handle it instead of throwing 409
        } catch (FareClient.PaymentServiceUnavailableException e) {
            throw new PaymentFailedException(e.getMessage());
        }

        // 3. Gracefully update the ride status to 'COMPLETED' so the frontend can proceed smoothly.
        ride.setStatus("COMPLETED");
        return rideRepository.save(ride);
    }

    public static class NoDriverAvailableException extends RuntimeException {
        public NoDriverAvailableException(String message) {
            super(message);
        }
    }

    public static class InvalidStatusTransitionException extends RuntimeException {
        public InvalidStatusTransitionException(String message) {
            super(message);
        }
    }

    public static class PaymentAlreadyExistsException extends RuntimeException {
        public PaymentAlreadyExistsException(String message) {
            super(message);
        }
    }

    public static class PaymentFailedException extends RuntimeException {
        public PaymentFailedException(String message) {
            super(message);
        }
    }
}
