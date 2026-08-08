package org.example.mypaintings.painting.styles;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaintingStyleTest {

    private static final String STYLE_NAME = "Impressionism";
    private static final long OFFICIAL_THRESHOLD = 10L;

    private PaintingStyle style;

    @BeforeEach
    void setUp() {
        // given
        style = new PaintingStyle(STYLE_NAME);
    }

    @Test
    @DisplayName("Constructor should set trimmed name and normalizedName automatically")
    void constructorShouldSetTrimmedNameAndNormalizedName() {
        // given
        PaintingStyle untrimmedStyle = new PaintingStyle("  Surrealism  ");

        // then
        assertThat(untrimmedStyle.getName()).isEqualTo("Surrealism");
        assertThat(untrimmedStyle.getNormalizedName()).isEqualTo("surrealism");
    }

    @Test
    @DisplayName("Constructor should throw exception when name is null or blank")
    void constructorShouldThrowExceptionWhenNameIsInvalid() {
        // when & then
        assertThatThrownBy(() -> new PaintingStyle(null))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new PaintingStyle("   "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Constructor should set isOfficial to false")
    void constructorShouldSetIsOfficialToFalse() {
        // when
        boolean isOfficial = style.isOfficial();

        // then
        assertThat(isOfficial).isFalse();
    }

    @Test
    @DisplayName("Constructor should set usageCount to zero")
    void constructorShouldSetUsageCountToZero() {
        // when
        long usageCount = style.getUsageCount();

        // then
        assertThat(usageCount).isZero();
    }

    @Test
    @DisplayName("IncrementUsageCount should increase usageCount by 1")
    void incrementUsageCountShouldIncrementUsageCount() {
        // when
        style.incrementUsageCount(OFFICIAL_THRESHOLD);

        // then
        assertThat(style.getUsageCount()).isEqualTo(1L);
    }

    @Test
    @DisplayName("IncrementUsageCount should not mark as official when usageCount is less than or equal to threshold")
    void incrementUsageCountShouldNotMarkAsOfficialWhenBelowOrEqualThreshold() {
        // when
        for (long i = 0; i < OFFICIAL_THRESHOLD; i++) {
            style.incrementUsageCount(OFFICIAL_THRESHOLD);
        }

        // then
        assertThat(style.getUsageCount()).isEqualTo(OFFICIAL_THRESHOLD);
        assertThat(style.isOfficial()).isFalse();
    }

    @Test
    @DisplayName("IncrementUsageCount should mark as official when usageCount exceeds threshold")
    void incrementUsageCountShouldMarkAsOfficialWhenAboveThreshold() {
        // when
        for (long i = 0; i <= OFFICIAL_THRESHOLD; i++) {
            style.incrementUsageCount(OFFICIAL_THRESHOLD);
        }

        // then
        assertThat(style.getUsageCount()).isEqualTo(OFFICIAL_THRESHOLD + 1);
        assertThat(style.isOfficial()).isTrue();
    }

    @Test
    @DisplayName("IncrementUsageCount should remain official on further increments")
    void incrementUsageCountShouldStayOfficialOnFurtherIncrements() {
        // given
        for (long i = 0; i <= OFFICIAL_THRESHOLD; i++) {
            style.incrementUsageCount(OFFICIAL_THRESHOLD);
        }

        // when
        style.incrementUsageCount(OFFICIAL_THRESHOLD);
        style.incrementUsageCount(OFFICIAL_THRESHOLD);

        // then
        assertThat(style.isOfficial()).isTrue();
        assertThat(style.getUsageCount()).isEqualTo(OFFICIAL_THRESHOLD + 3);
    }

    @Test
    @DisplayName("IncrementUsageCount should mark as official when threshold is zero")
    void incrementUsageCountShouldMarkAsOfficialWhenThresholdIsZero() {
        // when
        style.incrementUsageCount(0L);

        // then
        assertThat(style.isOfficial()).isTrue();
        assertThat(style.getUsageCount()).isEqualTo(1L);
    }

    @Test
    @DisplayName("Equals and hashCode should be equal for objects with the same normalizedName")
    void equalsShouldBeEqualWithSameNormalizedName() {
        // given
        PaintingStyle style1 = new PaintingStyle("Style A");
        PaintingStyle style2 = new PaintingStyle("style a"); // po normalizacji będą miały ten sam normalizedName

        // then
        assertThat(style1).isEqualTo(style2);
        assertThat(style1).hasSameHashCodeAs(style2);
    }

    @Test
    @DisplayName("Equals should not be equal for objects with different normalizedName")
    void equalsShouldNotBeEqualWithDifferentNormalizedName() {
        // given
        PaintingStyle style1 = new PaintingStyle("Style A");
        PaintingStyle style2 = new PaintingStyle("Style B");

        // then
        assertThat(style1).isNotEqualTo(style2);
    }

    @Test
    @DisplayName("Equals should be equal to itself")
    void equalsShouldBeEqualToItself() {
        // then
        assertThat(style).isEqualTo(style);
    }

    @Test
    @DisplayName("Equals should not be equal to null")
    void equalsShouldNotBeEqualToNull() {
        // then
        assertThat(style).isNotEqualTo(null);
    }
}