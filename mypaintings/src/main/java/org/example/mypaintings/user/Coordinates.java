package org.example.mypaintings.user;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.*;
import org.example.mypaintings.exception.validation.BusinessValidationException;


@Getter
@Embeddable
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@EqualsAndHashCode(of = {"latitude", "longitude","locationChanged"})
public class Coordinates {

    @Column(nullable = false)
    private double latitude;

    @Column(nullable = false)
    private double longitude;

    @Column(nullable = false)
    private boolean locationChanged;

    public Coordinates(double latitude, double longitude) {

        if (latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
            throw new BusinessValidationException("Invalid geographic coordinates");
        }
        this.latitude = latitude;
        this.longitude = longitude;
        this.locationChanged = true;
    }

    public Coordinates markAsUnchanged() {
        return new Coordinates(this.latitude, this.longitude, false);
    }

}
