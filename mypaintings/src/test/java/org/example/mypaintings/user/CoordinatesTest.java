package org.example.mypaintings.user;

import org.example.mypaintings.exception.validation.BusinessValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CoordinatesTest {

    private static final double VALID_LATITUDE = 52.3676;
    private static final double VALID_LONGITUDE = 4.9041;

    // ---------------------------------------------------------------------
    // Public constructor (double, double)
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("Constructor: creates coordinates with valid values and locationChanged=true")
    void constructor_shouldCreateCoordinates_whenValuesValid() {
        // when
        Coordinates coordinates = new Coordinates(VALID_LATITUDE, VALID_LONGITUDE);

        // then
        assertThat(coordinates.getLatitude()).isEqualTo(VALID_LATITUDE);
        assertThat(coordinates.getLongitude()).isEqualTo(VALID_LONGITUDE);
        assertThat(coordinates.isLocationChanged()).isTrue();
    }

    @Test
    @DisplayName("Constructor: accepts boundary latitude values (-90, 90)")
    void constructor_shouldAcceptBoundaryLatitude() {
        // when
        Coordinates southPole = new Coordinates(-90.0, 0.0);
        Coordinates northPole = new Coordinates(90.0, 0.0);

        // then
        assertThat(southPole.getLatitude()).isEqualTo(-90.0);
        assertThat(northPole.getLatitude()).isEqualTo(90.0);
    }

    @Test
    @DisplayName("Constructor: accepts boundary longitude values (-180, 180)")
    void constructor_shouldAcceptBoundaryLongitude() {
        // when
        Coordinates westEdge = new Coordinates(0.0, -180.0);
        Coordinates eastEdge = new Coordinates(0.0, 180.0);

        // then
        assertThat(westEdge.getLongitude()).isEqualTo(-180.0);
        assertThat(eastEdge.getLongitude()).isEqualTo(180.0);
    }

    @Test
    @DisplayName("Constructor: accepts (0, 0)")
    void constructor_shouldAcceptZeroCoordinates() {
        // when
        Coordinates origin = new Coordinates(0.0, 0.0);

        // then
        assertThat(origin.getLatitude()).isZero();
        assertThat(origin.getLongitude()).isZero();
        assertThat(origin.isLocationChanged()).isTrue();
    }

    @ParameterizedTest(name = "Constructor: throws when latitude {0} or longitude {1} is invalid")
    @CsvSource({
            "-90.0001, 0.0",
            "90.0001, 0.0",
            "0.0, -180.0001",
            "0.0, 180.0001",
            "200.0, 300.0",
            "NaN, 0.0",
            "0.0, NaN"
    })
    @DisplayName("Constructor: throws when coordinates are out of range or NaN")
    void constructor_shouldThrow_whenCoordinatesAreInvalid(double latitude, double longitude) {
        // when / then
        assertThatThrownBy(() -> new Coordinates(latitude, longitude))
                .isInstanceOf(BusinessValidationException.class)
                .hasMessage("Invalid geographic coordinates");
    }

    // ---------------------------------------------------------------------
    // markAsUnchanged
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("markAsUnchanged: returns new object with locationChanged=false and same coordinates")
    void markAsUnchanged_shouldReturnNewObjectWithLocationChangedFalse() {
        // given
        Coordinates original = new Coordinates(VALID_LATITUDE, VALID_LONGITUDE);

        // when
        Coordinates unchanged = original.markAsUnchanged();

        // then
        assertThat(unchanged).isNotSameAs(original);
        assertThat(unchanged.getLatitude()).isEqualTo(VALID_LATITUDE);
        assertThat(unchanged.getLongitude()).isEqualTo(VALID_LONGITUDE);
        assertThat(unchanged.isLocationChanged()).isFalse();
    }

    @Test
    @DisplayName("markAsUnchanged: does not mutate original object")
    void markAsUnchanged_shouldNotMutateOriginal() {
        // given
        Coordinates original = new Coordinates(VALID_LATITUDE, VALID_LONGITUDE);

        // when
        original.markAsUnchanged();

        // then
        assertThat(original.isLocationChanged()).isTrue();
    }

    @Test
    @DisplayName("markAsUnchanged: is idempotent — calling twice yields the same state")
    void markAsUnchanged_shouldBeIdempotent() {
        // given
        Coordinates original = new Coordinates(VALID_LATITUDE, VALID_LONGITUDE);

        // when
        Coordinates first = original.markAsUnchanged();
        Coordinates second = first.markAsUnchanged();

        // then
        assertThat(second.isLocationChanged()).isFalse();
        assertThat(second.getLatitude()).isEqualTo(first.getLatitude());
        assertThat(second.getLongitude()).isEqualTo(first.getLongitude());
    }

    @Test
    @DisplayName("markAsUnchanged: preserves boundary values")
    void markAsUnchanged_shouldPreserveBoundaryValues() {
        // given
        Coordinates northPole = new Coordinates(90.0, 180.0);

        // when
        Coordinates unchanged = northPole.markAsUnchanged();

        // then
        assertThat(unchanged.getLatitude()).isEqualTo(90.0);
        assertThat(unchanged.getLongitude()).isEqualTo(180.0);
        assertThat(unchanged.isLocationChanged()).isFalse();
    }

    // ---------------------------------------------------------------------
    // equals & hashCode
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("Equals: returns true when all fields match")
    void equals_shouldReturnTrue_whenAllFieldsMatch() {
        Coordinates a = new Coordinates(VALID_LATITUDE, VALID_LONGITUDE);
        Coordinates b = new Coordinates(VALID_LATITUDE, VALID_LONGITUDE);
        assertThat(a).isEqualTo(b);
        assertThat(a).hasSameHashCodeAs(b);
    }

    @Test
    @DisplayName("Equals: returns false when coordinates differ")
    void equals_shouldReturnFalse_whenCoordsDiffer() {
        Coordinates a = new Coordinates(VALID_LATITUDE, VALID_LONGITUDE);
        Coordinates b = new Coordinates(VALID_LATITUDE + 1.0, VALID_LONGITUDE + 1.0);
        assertThat(a).isNotEqualTo(b);
    }

    @Test
    @DisplayName("Equals: returns false when locationChanged flag differs")
    void equals_shouldReturnFalse_whenLocationChangedDiffers() {
        Coordinates changed = new Coordinates(VALID_LATITUDE, VALID_LONGITUDE);
        Coordinates unchanged = changed.markAsUnchanged();
        assertThat(changed).isNotEqualTo(unchanged);
    }
}