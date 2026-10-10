package com.ridelink.ride_service.model;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

// Ride lifecycle. Stored by name, so rides saved when status was a plain
// String ("ASSIGNED", "COMPLETED") load unchanged.
//
//   REQUESTED -> ASSIGNED -> ACCEPTED -> IN_PROGRESS -> COMPLETED
//        \           \           \
//         +-----------+-----------+--> CANCELLED   (no cancel once IN_PROGRESS)
public enum RideStatus {
    REQUESTED,
    ASSIGNED,
    ACCEPTED,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED;

    // The single source of truth for which moves are legal. Anything not
    // listed is rejected with 409 INVALID_STATUS_TRANSITION.
    private static final Map<RideStatus, EnumSet<RideStatus>> ALLOWED = new EnumMap<>(RideStatus.class);

    static {
        ALLOWED.put(REQUESTED, EnumSet.of(ASSIGNED, CANCELLED));
        ALLOWED.put(ASSIGNED, EnumSet.of(ACCEPTED, CANCELLED));
        ALLOWED.put(ACCEPTED, EnumSet.of(IN_PROGRESS, CANCELLED));
        ALLOWED.put(IN_PROGRESS, EnumSet.of(COMPLETED));
        ALLOWED.put(COMPLETED, EnumSet.noneOf(RideStatus.class));
        ALLOWED.put(CANCELLED, EnumSet.noneOf(RideStatus.class));
    }

    public boolean canTransitionTo(RideStatus next) {
        return next != null && ALLOWED.get(this).contains(next);
    }

    public Set<RideStatus> allowedNext() {
        return EnumSet.copyOf(ALLOWED.get(this));
    }

    public boolean isTerminal() {
        return ALLOWED.get(this).isEmpty();
    }
}
