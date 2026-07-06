package org.example.mypaintings.reservation.event;

public record ReservationAcceptedEvent(long reservationId, long paintingId, long hotelId) {
}