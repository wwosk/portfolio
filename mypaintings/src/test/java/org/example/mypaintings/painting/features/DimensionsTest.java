package org.example.mypaintings.painting.features;

import org.example.mypaintings.exception.validation.BusinessValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DimensionsTest {

    private static final double VALID_WIDTH = 50.0;
    private static final double VALID_HEIGHT = 70.0;
    private static final Unit VALID_UNIT = Unit.CM;

    // --- Constructor: valid data ---

    @Test
    @DisplayName("Constructor: creates dimensions with valid values")
    void shouldCreateWithValidDimensions() {
        Dimensions dimensions = new Dimensions(VALID_WIDTH, VALID_HEIGHT, VALID_UNIT);

        assertThat(dimensions.getWidth()).isEqualTo(VALID_WIDTH);
        assertThat(dimensions.getHeight()).isEqualTo(VALID_HEIGHT);
        assertThat(dimensions.getUnit()).isEqualTo(VALID_UNIT);
    }

    @Test
    @DisplayName("Constructor: accepts fractional dimensions")
    void shouldAcceptFractionalDimensions() {
        Dimensions dimensions = new Dimensions(12.5, 33.75, Unit.IN);

        assertThat(dimensions.getWidth()).isEqualTo(12.5);
        assertThat(dimensions.getHeight()).isEqualTo(33.75);
        assertThat(dimensions.getUnit()).isEqualTo(Unit.IN);
    }

    @Test
    @DisplayName("Constructor: accepts very small positive dimensions")
    void shouldAcceptVerySmallPositiveDimensions() {
        Dimensions dimensions = new Dimensions(0.001, 0.001, Unit.CM);

        assertThat(dimensions.getWidth()).isEqualTo(0.001);
        assertThat(dimensions.getHeight()).isEqualTo(0.001);
    }

    // --- Constructor: width validation ---

    @ParameterizedTest(name = "Constructor: throws when width={0} is not positive")
    @ValueSource(doubles = {0.0, -0.1, -1.0, -100.0, -0.001})
    @DisplayName("Constructor: throws BusinessValidationException when width <= 0")
    void shouldThrowWhenWidthIsNotPositive(double invalidWidth) {
        assertThatThrownBy(() -> new Dimensions(invalidWidth, VALID_HEIGHT, VALID_UNIT))
                .isInstanceOf(BusinessValidationException.class)
                .hasMessage("Invalid dimensions");
    }

    // --- Constructor: height validation ---

    @ParameterizedTest(name = "Constructor: throws when height={0} is not positive")
    @ValueSource(doubles = {0.0, -0.1, -1.0, -100.0, -0.001})
    @DisplayName("Constructor: throws BusinessValidationException when height <= 0")
    void shouldThrowWhenHeightIsNotPositive(double invalidHeight) {
        assertThatThrownBy(() -> new Dimensions(VALID_WIDTH, invalidHeight, VALID_UNIT))
                .isInstanceOf(BusinessValidationException.class)
                .hasMessage("Invalid dimensions");
    }

    // --- Constructor: combined dimensions validation ---

    @ParameterizedTest(name = "Constructor: throws when width={0}, height={1}")
    @CsvSource({
            "0,     0",
            "-1,   -1",
            "0,    50",
            "50,    0",
            "-1,   50",
            "50,   -1",
            "0,   -100",
            "-100,  0",
            "NaN,  50",
            "50,   NaN"
    })
    @DisplayName("Constructor: throws BusinessValidationException for invalid dimension combinations")
    void shouldThrowForInvalidDimensionCombinations(double width, double height) {
        assertThatThrownBy(() -> new Dimensions(width, height, VALID_UNIT))
                .isInstanceOf(BusinessValidationException.class)
                .hasMessage("Invalid dimensions");
    }

    // --- Constructor: unit validation ---

    @Test
    @DisplayName("Constructor: throws BusinessValidationException when unit is null")
    void shouldThrowWhenUnitIsNull() {
        assertThatThrownBy(() -> new Dimensions(VALID_WIDTH, VALID_HEIGHT, null))
                .isInstanceOf(BusinessValidationException.class)
                .hasMessage("Unit is required");
    }

    // --- Validation order ---

    @Test
    @DisplayName("Constructor: validates dimensions before unit")
    void shouldValidateDimensionsBeforeUnit() {
        assertThatThrownBy(() -> new Dimensions(0, 0, null))
                .isInstanceOf(BusinessValidationException.class)
                .hasMessage("Invalid dimensions");
    }

    // --- equals / hashCode ---

    @Test
    @DisplayName("Equals: returns true when all fields match")
    void shouldBeEqualWithSameFields() {
        Dimensions d1 = new Dimensions(VALID_WIDTH, VALID_HEIGHT, VALID_UNIT);
        Dimensions d2 = new Dimensions(VALID_WIDTH, VALID_HEIGHT, VALID_UNIT);

        assertThat(d1).isEqualTo(d2);
        assertThat(d1).hasSameHashCodeAs(d2);
    }

    @Test
    @DisplayName("Equals: returns false when fields differ")
    void shouldNotBeEqualWithDifferentFields() {
        Dimensions d1 = new Dimensions(VALID_WIDTH, VALID_HEIGHT, VALID_UNIT);
        Dimensions d2 = new Dimensions(VALID_WIDTH + 1, VALID_HEIGHT, VALID_UNIT);

        assertThat(d1).isNotEqualTo(d2);
    }

    @Test
    @DisplayName("Equals: returns true when compared to itself")
    void shouldBeEqualToItself() {
        Dimensions dimensions = new Dimensions(VALID_WIDTH, VALID_HEIGHT, VALID_UNIT);

        assertThat(dimensions).isEqualTo(dimensions);
    }

    @Test
    @DisplayName("Equals: returns false when compared to null")
    void shouldNotBeEqualToNull() {
        Dimensions dimensions = new Dimensions(VALID_WIDTH, VALID_HEIGHT, VALID_UNIT);

        assertThat(dimensions).isNotEqualTo(null);
    }
}