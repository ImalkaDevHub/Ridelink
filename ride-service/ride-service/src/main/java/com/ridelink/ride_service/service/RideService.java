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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class RideService {

    // Rides saved before vehicleType was required have none; Fare Service
    // needs one, and CAR prices like the old flat rate (multiplier 1.0).
    static final String LEGACY_DEFAULT_VEHICLE_TYPE = "CAR";

    @Autowired
    private RideRepository rideRepository;

    @Autowired
    private DriverClient driverClient;

    @Autowired
    private FareClient fareClient;

    // Spring injects a thread-bound proxy here even though this bean is a
    // singleton, so this always reflects the request currently being
    // handled - used to forward the caller's JWT to the other services.
    @Autowired
    private HttpServletRequest request;

    @Value("${ride.payment.auto-charge-on-complete:true}")
    private boolean autoChargeOnComplete;

    // REQUESTED -> ASSIGNED in one call. The driver is reserved atomically
    // by driver-service before anything is saved, so when no driver is
    // available nothing is stored (409), exactly as before.
    public Ride requestRide(Ride ride) {
        // Server-owned fields: never trust what the client sent.
        ride.setId(null);
        ride.setDriverId(null);
        ride.setDriverAccountId(null);
        ride.setEstimatedFare(null);
        ride.setFinalFare(null);
        ride.setAcceptedAt(null);
        ride.setStartedAt(null);
        ride.setCompletedAt(null);
        ride.setCancelledAt(null);
        ride.setCancelReason(null);

        // Ride creation requires authentication (see SecurityConfig), so the
        // caller's account id is always available here - recorded as the
        // payer for when this ride is later completed and paid for.
        ride.setPassengerId(currentCaller().id());
        ride.setStatus(RideStatus.REQUESTED);
        ride.setRequestedAt(Instant.now());

        String authorizationHeader = request.getHeader("Authorization");
        DriverResponse driver = driverClient.reserveDriver(ride.getVehicleType(), authorizationHeader);
        if (driver == null) {
            throw new NoDriverAvailableException("No driver is available right now.");
        }

        transition(ride, RideStatus.ASSIGNED);
        ride.setDriverId(driver.getId());
        ride.setDriverAccountId(driver.getAccountId());

        // Second interservice call (forwarding the caller's token). Best
        // effort: null when Fare is down.
        ride.setEstimatedFare(fareClient.estimateFare(new FareEstimateRequest(
                ride.getPickup(), ride.getDestination(),
                ride.getDistanceKm() != null ? ride.getDistanceKm() : ride.getDistance(),
                ride.getDurationMin() != null ? ride.getDurationMin()
                        : ride.getDuration() != null ? ride.getDuration().doubleValue() : null,
                ride.getVehicleType(),
                ride.getPickupLat(), ride.getPickupLng(), ride.getDestinationLat(), ride.getDestinationLng()),
                authorizationHeader));

        try {
            return rideRepository.save(ride);
        } catch (RuntimeException e) {
            // Don't leave the driver reserved for a ride that doesn't exist.
            driverClient.releaseDriver(driver.getId(), authorizationHeader);
            throw e;
        }
    }

    // Participants only: the ride's passenger, its assigned driver, or an
    // ADMIN.
    public Ride getRide(String id) {
        Ride ride = findOrThrow(id);
        Caller caller = currentCaller();
        if (!caller.isAdmin() && !isParticipant(ride, caller)) {
            throw new NotRideParticipantException("Only the ride's passenger or driver can view it.");
        }
        return ride;
    }

    // History. Passengers may only list their own rides; drivers only see
    // rides assigned to them. Without filters, callers get their own rides
    // (ADMIN: all rides).
    public List<Ride> findRides(String passengerId, String driverId, String status) {
        Caller caller = currentCaller();
        RideStatus statusFilter = parseStatus(status);

        if (caller.isPassenger() && passengerId != null && !passengerId.equals(caller.id())) {
            throw new NotRideParticipantException("Passengers can only list their own rides.");
        }
        if (caller.isPassenger() && driverId != null) {
            throw new NotRideParticipantException("Passengers can only list their own rides.");
        }

        List<Ride> rides;
        if (passengerId != null) {
            rides = rideRepository.findByPassengerIdOrderByIdDesc(passengerId);
        } else if (driverId != null) {
            rides = rideRepository.findByDriverIdOrderByIdDesc(driverId);
        } else if (caller.isAdmin()) {
            rides = statusFilter != null
                    ? rideRepository.findByStatusOrderByIdDesc(statusFilter)
                    : rideRepository.findAllByOrderByIdDesc();
        } else if (caller.isDriver()) {
            rides = rideRepository.findByDriverAccountIdOrderByIdDesc(caller.id());
        } else {
            rides = rideRepository.findByPassengerIdOrderByIdDesc(caller.id());
        }

        return rides.stream()
                .filter(ride -> caller.isAdmin() || isParticipant(ride, caller))
                .filter(ride -> statusFilter == null || ride.getStatus() == statusFilter)
                .toList();
    }

    // ASSIGNED -> ACCEPTED, by the assigned driver only.
    public Ride acceptRide(String id) {
        Ride ride = findOrThrow(id);
        requireAssignedDriver(ride);
        transition(ride, RideStatus.ACCEPTED);
        return rideRepository.save(ride);
    }

    // ACCEPTED -> IN_PROGRESS, by the assigned driver only.
    public Ride startRide(String id) {
        Ride ride = findOrThrow(id);
        requireAssignedDriver(ride);
        transition(ride, RideStatus.IN_PROGRESS);
        return rideRepository.save(ride);
    }

    // REQUESTED/ASSIGNED/ACCEPTED -> CANCELLED, by the passenger, the
    // assigned driver or an ADMIN. Not possible once IN_PROGRESS. The
    // driver is released (best effort).
    public Ride cancelRide(String id, String reason) {
        Ride ride = findOrThrow(id);
        Caller caller = currentCaller();
        boolean isPassenger = caller.id().equals(ride.getPassengerId());
        if (!caller.isAdmin() && !isPassenger && !isAssignedDriver(ride, caller)) {
            throw new NotRideParticipantException("Only the ride's passenger or driver can cancel it.");
        }

        transition(ride, RideStatus.CANCELLED);
        ride.setCancelReason(reason == null || reason.isBlank() ? null : reason.trim());
        Ride saved = rideRepository.save(ride);

        if (saved.getDriverId() != null) {
            driverClient.releaseDriver(saved.getDriverId(), request.getHeader("Authorization"));
        }
        return saved;
    }

    // IN_PROGRESS -> COMPLETED. Calls Fare & Payment Service for the final
    // fare first; if that fails nothing is saved, so the ride keeps its
    // current status and the call can simply be retried (the Fare side is
    // idempotent per ride).
    //
    // Backward compatibility: the original API completed rides straight
    // from ASSIGNED (accept/start didn't exist). A ride in ASSIGNED or
    // ACCEPTED is therefore walked through the remaining steps
    // (ASSIGNED -> ACCEPTED -> IN_PROGRESS -> COMPLETED), each one checked
    // against the transition map and timestamped, so the map itself stays
    // strict: COMPLETED is only ever entered from IN_PROGRESS.
    public Ride completeRide(String id, double distanceKm, double durationMin) {
        Ride ride = findOrThrow(id);
        requireAssignedDriverOrAdmin(ride);

        List<RideStatus> path = pathToCompleted(ride.getStatus());
        String authorizationHeader = request.getHeader("Authorization");
        String vehicleType = ride.getVehicleType() != null && !ride.getVehicleType().isBlank()
                ? ride.getVehicleType() : LEGACY_DEFAULT_VEHICLE_TYPE;

        BigDecimal finalFare = fareClient.finalizeFare(
                new FinalizeFareRequest(ride.getId(), distanceKm, durationMin, vehicleType, ride.getPassengerId()),
                authorizationHeader);

        if (autoChargeOnComplete) {
            try {
                fareClient.finalizePayment(ride.getId(), ride.getPassengerId(), distanceKm, durationMin,
                        vehicleType, authorizationHeader);
            } catch (FareClient.DuplicatePaymentException e) {
                // Already paid (e.g. a retried completion) - nothing to do.
            } catch (FareClient.PaymentServiceUnavailableException e) {
                throw new PaymentFailedException(e.getMessage());
            }
        }

        for (RideStatus next : path) {
            transition(ride, next);
        }
        ride.setFinalFare(finalFare);
        ride.setDistanceKm(distanceKm);
        ride.setDurationMin(durationMin);
        Ride saved = rideRepository.save(ride);

        if (saved.getDriverId() != null) {
            driverClient.releaseDriver(saved.getDriverId(), authorizationHeader);
        }
        return saved;
    }

    // Applies one transition if the map allows it, stamping its timestamp.
    static void transition(Ride ride, RideStatus next) {
        RideStatus current = ride.getStatus();
        if (current == null || !current.canTransitionTo(next)) {
            throw new InvalidStatusTransitionException(
                    "Cannot change ride status from " + current + " to " + next + ".");
        }
        ride.setStatus(next);
        Instant now = Instant.now();
        switch (next) {
            case ACCEPTED -> ride.setAcceptedAt(now);
            case IN_PROGRESS -> ride.setStartedAt(now);
            case COMPLETED -> ride.setCompletedAt(now);
            case CANCELLED -> ride.setCancelledAt(now);
            default -> {
            }
        }
    }

    // The statuses /complete steps through from the current one. Validated
    // up front so an invalid completion fails before any remote call.
    static List<RideStatus> pathToCompleted(RideStatus current) {
        List<RideStatus> path = new ArrayList<>();
        RideStatus step = current;
        while (step == RideStatus.ASSIGNED || step == RideStatus.ACCEPTED) {
            RideStatus next = step == RideStatus.ASSIGNED ? RideStatus.ACCEPTED : RideStatus.IN_PROGRESS;
            path.add(next);
            step = next;
        }
        if (step == null || !step.canTransitionTo(RideStatus.COMPLETED)) {
            throw new InvalidStatusTransitionException(
                    "Cannot complete a ride with status: " + current);
        }
        path.add(RideStatus.COMPLETED);
        return path;
    }

    private Ride findOrThrow(String id) {
        return rideRepository.findById(id)
                .orElseThrow(() -> new RideNotFoundException("Ride not found with id: " + id));
    }

    private void requireAssignedDriver(Ride ride) {
        if (!isAssignedDriver(ride, currentCaller())) {
            throw new NotRideParticipantException("Only the driver assigned to this ride can do this.");
        }
    }

    private boolean isParticipant(Ride ride, Caller caller) {
        return (caller.id() != null && caller.id().equals(ride.getPassengerId())) || isAssignedDriver(ride, caller);
    }

    // /complete keeps ADMIN as well: the original endpoint allowed it.
    private void requireAssignedDriverOrAdmin(Ride ride) {
        Caller caller = currentCaller();
        if (!caller.isAdmin() && !isAssignedDriver(ride, caller)) {
            throw new NotRideParticipantException("Only the driver assigned to this ride can do this.");
        }
    }

    // Rides created before driverAccountId was recorded can't be tied to an
    // account, so any DRIVER is accepted for those (the original behaviour).
    private boolean isAssignedDriver(Ride ride, Caller caller) {
        if (!caller.isDriver()) {
            return false;
        }
        return ride.getDriverAccountId() == null || ride.getDriverAccountId().equals(caller.id());
    }

    private RideStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return RideStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new InvalidStatusFilterException("status must be one of " + java.util.Arrays.toString(RideStatus.values()));
        }
    }

    private Caller currentCaller() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String id = authentication != null && authentication.getPrincipal() != null
                ? authentication.getPrincipal().toString() : null;
        String role = authentication == null ? null : authentication.getAuthorities().stream()
                .map(authority -> authority.getAuthority())
                .findFirst().orElse(null);
        return new Caller(id, role);
    }

    record Caller(String id, String role) {
        boolean isAdmin() {
            return "ADMIN".equals(role);
        }

        boolean isDriver() {
            return "DRIVER".equals(role);
        }

        boolean isPassenger() {
            return "PASSENGER".equals(role);
        }
    }

    public static class RideNotFoundException extends RuntimeException {
        public RideNotFoundException(String message) {
            super(message);
        }
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

    public static class InvalidStatusFilterException extends RuntimeException {
        public InvalidStatusFilterException(String message) {
            super(message);
        }
    }

    public static class NotRideParticipantException extends RuntimeException {
        public NotRideParticipantException(String message) {
            super(message);
        }
    }

    public static class PaymentFailedException extends RuntimeException {
        public PaymentFailedException(String message) {
            super(message);
        }
    }
}
