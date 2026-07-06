package org.example.mypaintings.reservation;



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



}
