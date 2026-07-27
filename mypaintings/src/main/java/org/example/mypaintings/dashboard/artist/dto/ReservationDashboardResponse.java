package org.example.mypaintings.dashboard.artist.dto;

import lombok.Builder;
import org.example.mypaintings.reservation.ReservationStatus;

import java.time.Instant;
import java.util.List;

@Builder
public record ReservationDashboardResponse(
        long reservationId,
        long paintingId,
        String paintingTitle,
        String paintingUrl,
        Instant offerDate,
        ReservationStatus status,
        List<ReservationStatus> possibleTransitions
) {}