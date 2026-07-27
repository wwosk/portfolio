package org.example.mypaintings.dashboard.hotel.dto;

import lombok.Builder;
import org.example.mypaintings.painting.features.Dimensions;
import org.example.mypaintings.reservation.ReservationStatus;

@Builder
public record ReservationDashboardResponse(
    long reservationId,
    long paintingId,
    long artistId,
    String title,
    String imageUrl,
    Dimensions dimensions,
    ReservationStatus status,
    boolean isCancellable
){}
