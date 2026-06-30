package org.example.mypaintings.painting.features;

import org.example.mypaintings.exception.validation.BusinessValidationException;



public enum SizeCategory {
    SMALL,
    MEDIUM,
    LARGE,
    HUGE;

    public static SizeCategory calculateSizeCategory(Dimensions dimensions)
    {
        if (dimensions.getHeight() <= 0 || dimensions.getWidth() <= 0) throw new BusinessValidationException("Invalid dimensions");
        double longestEdge = Math.max(dimensions.getHeight(), dimensions.getWidth());

        double longestEdgeCm = switch (dimensions.getUnit()) {
            case IN -> longestEdge * 2.54;
            case CM -> longestEdge;
        };
        if (longestEdgeCm <= 40) return SizeCategory.SMALL;
        if (longestEdgeCm <= 100) return SizeCategory.MEDIUM;
        if (longestEdgeCm <= 180) return SizeCategory.LARGE;
        return SizeCategory.HUGE;
    }
}
