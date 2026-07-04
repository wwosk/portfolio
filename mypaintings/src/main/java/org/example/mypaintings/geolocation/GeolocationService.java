package org.example.mypaintings.geolocation;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.mypaintings.user.User;
import org.example.mypaintings.user.UserQueryService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.example.mypaintings.geolocation.GeoUtils.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class GeolocationService {
    private final UserProximityRepository userProximityRepository;
    private final UserQueryService userQueryService;

    @Value("${app.geolocation.search-radius:800.0}")
    private double searchRadius;

    @PostConstruct
    void validateSearchRadius() {
        if (searchRadius <= 0 || searchRadius > 20000) {
            throw new IllegalStateException(
                    "app.geolocation.search-radius must be in (0, 20000], got: " + searchRadius
            );
        }
        log.info("Geolocation search radius set to {} km", searchRadius);
    }


    @Transactional
    public void fullScanForNewHotel(long hotelId, double radius) {
        User hotel = userQueryService.getValidHotelOrThrow(hotelId);
        double lat = hotel.getCoordinates().getLatitude();
        double lon = hotel.getCoordinates().getLongitude();
        double dLat = getLatDelta(radius);
        double dLon = getLonDelta(radius, lat);

        double minLat = GeoUtils.clampLat(lat - dLat);
        double maxLat = GeoUtils.clampLat(lat + dLat);
        double minLon = GeoUtils.normalizeLon(lon - dLon);
        double maxLon = GeoUtils.normalizeLon(lon + dLon);

        List<User> potentialArtists = userQueryService.getArtistsInArea(
                minLat, maxLat, minLon, maxLon);

        Map<Long, UserProximity> existingByArtistId =
                userProximityRepository.findAllByUserAndNearbyUserIn(hotel, potentialArtists)
                        .stream()
                        .collect(Collectors.toMap(up -> up.getNearbyUser().getId(),
                                Function.identity()));

        Instant now = Instant.now();
        List<UserProximity> toSave = new ArrayList<>();
        List<UserProximity> toDelete = new ArrayList<>();

        for (User artist : potentialArtists) {
            double distance = calculateLatitudeLongitudeDistance(
                    lat, lon,
                    artist.getCoordinates().getLatitude(),
                    artist.getCoordinates().getLongitude());

            UserProximity existing = existingByArtistId.get(artist.getId());

            if (distance <= radius) {
                if (existing != null) {
                    existing.setDistanceKm(distance);
                    existing.setUpdatedAt(now);
                    toSave.add(existing);
                } else {
                    UserProximity np = new UserProximity();
                    np.setUser(hotel);
                    np.setNearbyUser(artist);
                    np.setDistanceKm(distance);
                    np.setUpdatedAt(now);
                    toSave.add(np);
                }
            } else if (existing != null) {
                toDelete.add(existing);
            }
        }

        userProximityRepository.saveAll(toSave);
        userProximityRepository.deleteAllInBatch(toDelete);
    }
}