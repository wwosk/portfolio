package org.example.mypaintings.reservation.event;

public record ReservationRevokedEvent(
        long reservationId,
        long paintingId
) {
}