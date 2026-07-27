package org.example.mypaintings.dashboard.artist.dto;

import lombok.Builder;
import org.example.mypaintings.painting.placement.PlacementStatus;

import java.time.Instant;
import java.util.List;

@Builder
public record PlacementDashboardResponse(
    long paintingId,
    String paintingUrl,
    String paintingTitle,
    PlacementStatus currentStatus,
    List<PlacementStatus> possibleTransitions,
    Instant placementDate
){}
