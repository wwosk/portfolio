package org.example.mypaintings.dashboard.hotel.dto;


import lombok.Builder;
import org.example.mypaintings.painting.features.Dimensions;
import org.example.mypaintings.painting.placement.PlacementStatus;

import java.time.Instant;
import java.util.List;

@Builder
public record PaintingStockDashboardResponse(
        long paintingId,
        String title,
        String imageUrl,
        String note,
        Dimensions dimensions,
        List<PlacementStatus> possibleTransitions,
        PlacementStatus currentStatus,
        Instant placementDate,
        String fullAddress
){}
