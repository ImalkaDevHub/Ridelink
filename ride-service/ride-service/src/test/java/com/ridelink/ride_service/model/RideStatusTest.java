package com.ridelink.ride_service.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static com.ridelink.ride_service.model.RideStatus.*;

// Checks every one of the 36 (from, to) pairs against the intended
// lifecycle, so any change to the transition map shows up here.
class RideStatusTest {

    private static final Set<String> VALID = Set.of(
            "REQUESTED>ASSIGNED", "REQUESTED>CANCELLED",
            "ASSIGNED>ACCEPTED", "ASSIGNED>CANCELLED",
            "ACCEPTED>IN_PROGRESS", "ACCEPTED>CANCELLED",
            "IN_PROGRESS>COMPLETED");

    static Stream<Arguments> everyPair() {
        return Stream.of(RideStatus.values())
                .flatMap(from -> Stream.of(RideStatus.values()).map(to -> Arguments.of(from, to)));
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("everyPair")
    void transitionIsAllowedOnlyIfItIsInTheLifecycle(RideStatus from, RideStatus to) {
        assertThat(from.canTransitionTo(to)).isEqualTo(VALID.contains(from + ">" + to));
    }

    @Test
    void noCancelOnceInProgress() {
        assertThat(IN_PROGRESS.canTransitionTo(CANCELLED)).isFalse();
    }

    @Test
    void completedIsOnlyReachableFromInProgress() {
        for (RideStatus from : RideStatus.values()) {
            assertThat(from.canTransitionTo(COMPLETED)).isEqualTo(from == IN_PROGRESS);
        }
    }

    @Test
    void terminalStatesHaveNoWayOut() {
        assertThat(COMPLETED.isTerminal()).isTrue();
        assertThat(CANCELLED.isTerminal()).isTrue();
        assertThat(COMPLETED.allowedNext()).isEmpty();
        assertThat(ASSIGNED.allowedNext()).isEqualTo(EnumSet.of(ACCEPTED, CANCELLED));
    }

    @Test
    void nullTargetIsRejected() {
        assertThat(ASSIGNED.canTransitionTo(null)).isFalse();
    }

    @Test
    void everyStatusIsReachableFromRequested() {
        Set<RideStatus> reached = EnumSet.of(REQUESTED);
        boolean grew = true;
        while (grew) {
            Set<RideStatus> next = EnumSet.copyOf(reached);
            reached.forEach(status -> next.addAll(status.allowedNext()));
            grew = next.size() > reached.size();
            reached = next;
        }
        assertThat(reached).containsExactlyInAnyOrder(RideStatus.values());
    }
}
