package org.example.mypaintings.feed.dto;

import org.example.mypaintings.painting.features.Dimensions;
import org.example.mypaintings.painting.features.Orientation;
import org.example.mypaintings.painting.features.PaintingMedium;
import org.example.mypaintings.painting.features.SizeCategory;
import org.example.mypaintings.sale.financial.Money;

import java.math.BigDecimal;
import java.util.List;

public record PaintingDetailsResponse(
        Long id,
        String title,
        String description,
        Money price,
        List<String> imageUrls,
        boolean isLiked,
        Long artistId,
        String artistNickname,
        Dimensions dimensions,
        SizeCategory sizeCategory,
        Orientation orientation,
        PaintingMedium medium,
        List<String> styles
) {}