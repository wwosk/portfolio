package org.example.mypaintings.painting.features;

import org.example.mypaintings.exception.validation.BusinessValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrientationTest {

    @ParameterizedTest(name = "Wysokość: {0}, Szerokość: {1} -> {2}")
    @CsvSource({
            "50.0, 100.0, PORTRAIT",
            "100.0, 50.0, LANDSCAPE",
            "75.0, 75.0, SQUARE"
    })
    @DisplayName("Powinien poprawnie obliczyć orientację dla poprawnych wymiarów")
    void shouldCalculateOrientationCorrectly(double height, double width, Orientation expectedOrientation) {
        // given
        Dimensions dimensions = new Dimensions(height, width, Unit.CM);

        // when
        Orientation orientation = Orientation.calculateOrientation(dimensions);

        // then
        assertThat(orientation).isNotNull().isEqualTo(expectedOrientation);
    }

    @ParameterizedTest(name = "Wysokość: {0}, Szerokość: {1} - rzuca wyjątek")
    @CsvSource({
            "0.0, 50.0",
            "50.0, 0.0",
            "-10.0, 50.0",
            "50.0, -5.0",
            "0.0, 0.0"
    })
    @DisplayName("Powinien rzucić BusinessValidationException dla wymiarów mniejszych lub równych zero")
    void shouldThrowExceptionWhenDimensionsAreInvalid(double height, double width) {
        // when & then
        assertThatThrownBy(() -> new Dimensions(height, width, Unit.CM))
                .isInstanceOf(BusinessValidationException.class)
                .hasMessage("Invalid dimensions");
    }
}