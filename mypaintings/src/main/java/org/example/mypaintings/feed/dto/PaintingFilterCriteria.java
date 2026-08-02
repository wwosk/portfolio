package org.example.mypaintings.feed.dto;
import org.example.mypaintings.painting.features.Orientation;
import org.example.mypaintings.painting.features.PaintingMedium;
import org.example.mypaintings.painting.features.SizeCategory;
import org.example.mypaintings.painting.styles.PaintingStyle;
import java.util.List;


public record PaintingFilterCriteria(
        Double radiusKm,
        List<PaintingStyle> style,
        List<SizeCategory> sizeCategory,
        List<PaintingMedium> medium,
        List<Orientation> orientation
) {}