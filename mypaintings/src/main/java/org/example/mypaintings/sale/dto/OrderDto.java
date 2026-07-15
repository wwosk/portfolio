package org.example.mypaintings.sale.dto;


import org.example.mypaintings.painting.features.Dimensions;
import org.example.mypaintings.painting.placement.PlacementStatus;

import java.time.Instant;
import java.util.List;

public record OrderDto(
        Long paintingId,
        String title,
        String imageFileKey,
        String note,
        Dimensions dimensions,
        List<PlacementStatus> possibleTransitions,
        String fullAddress,
        Instant saleDate
) {}