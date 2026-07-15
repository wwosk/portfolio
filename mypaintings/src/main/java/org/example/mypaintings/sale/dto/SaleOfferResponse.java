package org.example.mypaintings.sale.dto;

import lombok.Builder;
import org.example.mypaintings.painting.features.Dimensions;
import org.example.mypaintings.sale.financial.CostStructure;

import java.time.Instant;
import java.util.List;


@Builder
public record SaleOfferResponse(
        long paintingId,
        String title,
        String artistName,
        List<String> imageUrls,
        Dimensions dimensions,
        CostStructure costWithDelivery,
        CostStructure costWithSelfReception,
        boolean isLocked,
        Instant lockExpiresAt
) {}