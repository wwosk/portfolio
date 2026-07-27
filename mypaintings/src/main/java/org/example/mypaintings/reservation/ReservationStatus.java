package org.example.mypaintings.reservation;


import java.util.Arrays;
import java.util.List;
import java.util.Set;

public enum ReservationStatus {
    PENDING, ACCEPTED, REJECTED, CANCELLED, REVOKED, ARCHIVED;

    public boolean canTransitionTo(ReservationStatus nextStatus) {
        if (this == nextStatus) {
            return true;
        }
        return switch (this) {
            case PENDING -> nextStatus != REVOKED;
            case ACCEPTED -> nextStatus == REVOKED;
            case REJECTED, CANCELLED, REVOKED -> nextStatus == PENDING || nextStatus == ARCHIVED;
            case ARCHIVED -> false;
        };
    }

    public List<ReservationStatus> getPossibleTransitionsForArtist() {
        return getPossibleTransitions().stream()
                .filter(ARTIST_ACTIONS::contains)
                .toList();
    }

    public List<ReservationStatus> getPossibleTransitions() {
        return Arrays.stream(ReservationStatus.values())
                .filter(this::canTransitionTo)
                .toList();
    }

    private static final Set<ReservationStatus> ARTIST_ACTIONS = Set.of(
            ACCEPTED,
            REJECTED,
            REVOKED
    );



}
