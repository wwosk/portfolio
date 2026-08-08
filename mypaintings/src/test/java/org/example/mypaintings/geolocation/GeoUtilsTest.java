package org.example.mypaintings.geolocation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class GeoUtilsTest {

    private static final double EPS = 1e-9;

    @Nested
    @DisplayName("Distance calculation (Haversine)")
    class Distance {

        @Test
        @DisplayName("Should return zero when calculating distance between identical points")
        void identicalPointsReturnZero() {
            // when
            double distance = GeoUtils.calculateLatitudeLongitudeDistance(
                    52.2297, 21.0122, 52.2297, 21.0122);

            // then
            assertThat(distance).isZero();
        }

        @Test
        @DisplayName("Should return about 111km for one degree of latitude difference")
        void oneDegreeOfLatitudeIsAbout111Km() {
            // when
            double distance = GeoUtils.calculateLatitudeLongitudeDistance(0, 0, 1, 0);

            // then
            assertThat(distance).isCloseTo(111.19, within(0.5));
        }

        @Test
        @DisplayName("Should return about 252km for distance between Warsaw and Krakow")
        void warsawToKrakowIsAbout252Km() {
            // when
            double distance = GeoUtils.calculateLatitudeLongitudeDistance(
                    52.2297, 21.0122, 50.0647, 19.9450);

            // then
            assertThat(distance).isCloseTo(252.0, within(2.0));
        }

        @Test
        @DisplayName("Should return half earth circumference for antipodal points")
        void antipodalPointsAreHalfCircumference() {
            // when
            double distance = GeoUtils.calculateLatitudeLongitudeDistance(0, 0, 0, 180);

            // then
            assertThat(distance).isCloseTo(20015.0, within(5.0));
        }

        @Test
        @DisplayName("Should calculate short distance when crossing the antimeridian")
        void crossingAntimeridianIsShortDistance() {
            // when
            double distance = GeoUtils.calculateLatitudeLongitudeDistance(0, 179, 0, -179);

            // then
            assertThat(distance).isCloseTo(222.4, within(1.0));
        }

        @Test
        @DisplayName("Should be symmetric when swapping start and end points")
        void distanceIsSymmetric() {
            // when
            double ab = GeoUtils.calculateLatitudeLongitudeDistance(52.2, 21.0, 50.0, 19.9);
            double ba = GeoUtils.calculateLatitudeLongitudeDistance(50.0, 19.9, 52.2, 21.0);

            // then
            assertThat(ab).isCloseTo(ba, within(EPS));
        }
    }

    @Nested
    @DisplayName("Normalize longitude")
    class NormalizeLon {

        @ParameterizedTest(name = "normalizeLon({0}) should return {1}")
        @CsvSource({
                "0,      0",
                "100,    100",
                "-100,   -100",
                "180,    -180",
                "-180,   -180",
                "181,    -179",
                "-181,   179",
                "360,    0",
                "540,    -180",
                "-540,   -180"
        })
        @DisplayName("Should map longitude into -180 to 180 range correctly")
        void mapsIntoMinus180To180(double input, double expected) {
            // when
            double normalized = GeoUtils.normalizeLon(input);

            // then
            assertThat(normalized).isCloseTo(expected, within(EPS));
        }
    }

    @Nested
    @DisplayName("Longitude delta calculation")
    class LonDelta {

        @Test
        @DisplayName("Should return full range (180.0) at the poles")
        void atPoleReturnsFullRange() {
            // when & then
            assertThat(GeoUtils.getLonDelta(1.0, 90.0)).isEqualTo(180.0);
            assertThat(GeoUtils.getLonDelta(1.0, -90.0)).isEqualTo(180.0);
        }

        @Test
        @DisplayName("Should equal latitude delta at the equator")
        void atEquatorEqualsLatDelta() {
            // given
            double km = 111.0;

            // when
            double lonDelta = GeoUtils.getLonDelta(km, 0.0);

            // then
            assertThat(lonDelta).isCloseTo(GeoUtils.getLatDelta(km), within(EPS));
        }

        @Test
        @DisplayName("Should double at 60 degrees latitude compared to equator")
        void at60DegreesIsDoubleOfEquator() {
            // given
            double km = 111.0;

            // when
            double lonDelta = GeoUtils.getLonDelta(km, 60.0);

            // then
            assertThat(lonDelta).isCloseTo(2 * GeoUtils.getLonDelta(km, 0.0), within(1e-6));
        }
    }

    @Nested
    @DisplayName("Latitude delta calculation")
    class LatDelta {

        @Test
        @DisplayName("Should convert kilometers to degrees correctly")
        void convertsKmToDegrees() {
            // when
            double latDelta = GeoUtils.getLatDelta(111.0);

            // then
            assertThat(latDelta).isCloseTo(1.0, within(1e-6));
        }

        @Test
        @DisplayName("Should cap at 90 degrees for very large distances")
        void capsAt90Degrees() {
            // when
            double latDelta = GeoUtils.getLatDelta(20_000);

            // then
            assertThat(latDelta).isEqualTo(90.0);
        }
    }

    @Nested
    @DisplayName("Latitude clamping")
    class ClampLat {

        @Test
        @DisplayName("Should keep range when inside boundaries and clamp when outside")
        void clampLatKeepsRangeAndClampsOutside() {
            // when & then
            assertThat(GeoUtils.clampLat(45.0)).isEqualTo(45.0);
            assertThat(GeoUtils.clampLat(1000)).isEqualTo(90.0);
            assertThat(GeoUtils.clampLat(-1000)).isEqualTo(-90.0);
        }
    }
}