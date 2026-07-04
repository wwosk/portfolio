package org.example.mypaintings.geolocation;


public class GeoUtils {

    private static final double KM_PER_LAT_DEGREE = 111.0;
    private static final double MIN_COS_LAT = 1e-6;
    private static final double EARTH_RADIUS_KM = 6371.0;

    private GeoUtils() {
    }

    public static double getLatDelta(double km) {
        return Math.min(km / KM_PER_LAT_DEGREE, 90.0);
    }

    public static double getLonDelta(double km, double lat) {
        double cosLat = Math.cos(Math.toRadians(lat));
        if (cosLat < MIN_COS_LAT) {
            return 180.0;
        }
        double delta = km / (KM_PER_LAT_DEGREE * cosLat);
        return Math.min(delta, 180.0);
    }

    public static double clampLat(double lat) {
        return Math.clamp(lat, -90.0, 90.0);
    }

    public static double normalizeLon(double lon) {
        return ((lon + 180.0) % 360.0 + 360.0) % 360.0 - 180.0;
    }

    public static double calculateLatitudeLongitudeDistance(
            double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_KM * c;
    }
}