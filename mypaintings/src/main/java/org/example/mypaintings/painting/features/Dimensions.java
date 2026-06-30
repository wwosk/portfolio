package org.example.mypaintings.painting.features;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.Value;
import org.example.mypaintings.exception.validation.BusinessValidationException;



@Embeddable
@Value
@NoArgsConstructor(force = true, access = AccessLevel.PROTECTED)
public class Dimensions {
    @Column(nullable = false)
    double width;
    @Column(nullable = false)
    double height;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    Unit unit;

    public Dimensions(double width, double height, Unit unit) {
        if (width <= 0 || height <= 0) {
            throw new BusinessValidationException("Invalid dimensions");
        }
        if (unit == null) {
            throw new BusinessValidationException("Unit is required");
        }
        this.width = width;
        this.height = height;
        this.unit = unit;
    }

}