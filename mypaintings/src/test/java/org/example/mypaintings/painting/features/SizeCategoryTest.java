package org.example.mypaintings.painting.features;

import org.example.mypaintings.exception.validation.BusinessValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SizeCategoryTest {

    @ParameterizedTest(name = "Height: {0}, Width: {1}, Unit: {2} -> {3}")
    @CsvSource({
            "30.0, 20.0, CM, SMALL",
            "40.0, 10.0, CM, SMALL",     // Boundary value for SMALL (<= 40)
            "80.0, 50.0, CM, MEDIUM",
            "100.0, 50.0, CM, MEDIUM",   // Boundary value for MEDIUM (<= 100)
            "150.0, 100.0, CM, LARGE",
            "180.0, 100.0, CM, LARGE",   // Boundary value for LARGE (<= 180)
            "200.0, 120.0, CM, HUGE",
            "180.1, 100.0, CM, HUGE",    // Just above the threshold for HUGE
            "15.0, 10.0, IN, SMALL"      // Inches conversion
    })
    @DisplayName("Should correctly calculate size category considering thresholds and units")
    void shouldCalculateSizeCategoryCorrectly(double height, double width, Unit unit, SizeCategory expectedCategory) {
        // given
        Dimensions dimensions = new Dimensions(height, width, unit);

        // when
        SizeCategory category = SizeCategory.calculateSizeCategory(dimensions);

        // then
        assertThat(category).isNotNull().isEqualTo(expectedCategory);
    }

    @ParameterizedTest(name = "Height: {0}, Width: {1} - throws exception")
    @CsvSource({
            "0.0, 50.0",
            "50.0, 0.0",
            "-10.0, 50.0",
            "50.0, -5.0",
            "0.0, 0.0"
    })
    @DisplayName("Should throw BusinessValidationException for dimensions less than or equal to zero")
    void shouldThrowExceptionWhenDimensionsAreInvalid(double height, double width) {
        // when & then
        assertThatThrownBy(() -> new Dimensions(height, width, Unit.CM))
                .isInstanceOf(BusinessValidationException.class)
                .hasMessage("Invalid dimensions");
    }
}