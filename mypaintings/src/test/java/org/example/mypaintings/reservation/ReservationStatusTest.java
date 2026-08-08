package org.example.mypaintings.reservation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class ReservationStatusTest {

    @ParameterizedTest(name = "canTransitionTo: from {0} to {1} should return {2}")
    @CsvSource({
            // PENDING
            "PENDING, PENDING, true",
            "PENDING, ACCEPTED, true",
            "PENDING, REJECTED, true",
            "PENDING, CANCELLED, true",
            "PENDING, REVOKED, false",
            "PENDING, ARCHIVED, true",

            // ACCEPTED
            "ACCEPTED, ACCEPTED, true",
            "ACCEPTED, REVOKED, true",
            "ACCEPTED, PENDING, false",

            // REJECTED / CANCELLED / REVOKED (shared rules)
            "REJECTED, PENDING, true",
            "REJECTED, ARCHIVED, true",
            "REJECTED, ACCEPTED, false",
            "CANCELLED, PENDING, true",
            "REVOKED, PENDING, true",

            // ARCHIVED (terminal state)
            "ARCHIVED, ARCHIVED, true",
            "ARCHIVED, PENDING, false"
    })
    @DisplayName("Should correctly validate allowed and forbidden state transitions on enum level")
    void canTransitionTo_shouldValidateStateTransitions(ReservationStatus current, ReservationStatus next, boolean expectedResult) {
        // when
        boolean result = current.canTransitionTo(next);

        // then
        assertThat(result).isEqualTo(expectedResult);
    }

    @ParameterizedTest(name = "getPossibleTransitions: status {0} should return consistent transition list")
    @MethodSource("provideAllStatuses")
    @DisplayName("Should return only statuses that fulfill canTransitionTo rule")
    void getPossibleTransitions_shouldReturnConsistentTransitions(ReservationStatus status) {
        // when
        List<ReservationStatus> transitions = status.getPossibleTransitions();

        // then
        assertThat(transitions).isNotEmpty();
        for (ReservationStatus next : ReservationStatus.values()) {
            if (status.canTransitionTo(next)) {
                assertThat(transitions).contains(next);
            } else {
                assertThat(transitions).doesNotContain(next);
            }
        }
    }

    @ParameterizedTest(name = "getPossibleTransitionsForArtist: status {0} should correctly filter artist actions")
    @MethodSource("provideAllStatuses")
    @DisplayName("Should return artist actions as a subset of valid transitions restricted to allowed pool")
    void getPossibleTransitionsForArtist_shouldCorrectlyFilterArtistTransitions(ReservationStatus status) {
        // when
        List<ReservationStatus> artistTransitions = status.getPossibleTransitionsForArtist();
        List<ReservationStatus> allTransitions = status.getPossibleTransitions();

        // then
        assertThat(allTransitions).containsAll(artistTransitions);

        for (ReservationStatus artistAction : artistTransitions) {
            assertThat(artistAction).isIn(
                    ReservationStatus.ACCEPTED,
                    ReservationStatus.REJECTED,
                    ReservationStatus.REVOKED
            );
        }
    }

    private static Stream<Arguments> provideAllStatuses() {
        return Stream.of(ReservationStatus.values()).map(Arguments::of);
    }
}