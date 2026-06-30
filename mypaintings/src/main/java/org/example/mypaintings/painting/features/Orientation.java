package org.example.mypaintings.painting.features;

import org.example.mypaintings.exception.validation.BusinessValidationException;



public enum Orientation {
    PORTRAIT, LANDSCAPE, SQUARE;

    public static Orientation calculateOrientation(Dimensions dimensions) {

        if (dimensions.getHeight() <= 0 || dimensions.getWidth() <= 0) throw new BusinessValidationException("Invalid dimensions");
        Orientation orientation;
        double h = dimensions.getHeight();
        double w = dimensions.getWidth();

        if (h > w) orientation = Orientation.PORTRAIT;
        else if (w > h) orientation = Orientation.LANDSCAPE;
        else orientation = Orientation.SQUARE;
        return orientation;
    }
}