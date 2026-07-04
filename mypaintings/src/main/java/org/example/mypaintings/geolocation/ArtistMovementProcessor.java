package org.example.mypaintings.geolocation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.mypaintings.user.User;
import org.example.mypaintings.user.UserQueryService;
import org.example.mypaintings.user.UserRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.example.mypaintings.geolocation.GeoUtils.calculateLatitudeLongitudeDistance;



@Service
@RequiredArgsConstructor
@Slf4j
public class ArtistMovementProcessor {

    private final UserQueryService userQueryService;
    private final UserProximityRepository userProximityRepository;
    private final UserRepository userRepository;

    @Transactional
    public void processArtistMovement(Long artistId, double searchRadius) {
        User artist = userRepository.findById(artistId).orElse(null);
        if (artist == null) return;
        if (!artist.getCoordinates().isLocationChanged()) return;

        double lat = artist.getCoordinates().getLatitude();
        double lon = artist.getCoordinates().getLongitude();
        double dLat = GeoUtils.getLatDelta(searchRadius);
        double dLon = GeoUtils.getLonDelta(searchRadius, lat);

        double minLat = GeoUtils.clampLat(lat - dLat);
        double maxLat = GeoUtils.clampLat(lat + dLat);
        double minLon = GeoUtils.normalizeLon(lon - dLon);
        double maxLon = GeoUtils.normalizeLon(lon + dLon);

        List<User> nearbyHotels = userQueryService.getHotelsInArea(
                minLat, maxLat, minLon, maxLon);

        Map<Long, UserProximity> existingByHotelId =
                userProximityRepository.findAllByNearbyUserAndUserIn(artist, nearbyHotels)
                        .stream()
                        .collect(Collectors.toMap(
                                up -> up.getUser().getId(),
                                Function.identity()));

        Instant now = Instant.now();
        List<UserProximity> toSave = new ArrayList<>();
        List<UserProximity> toDelete = new ArrayList<>();

        for (User hotel : nearbyHotels) {
            double distance = calculateLatitudeLongitudeDistance(
                    hotel.getCoordinates().getLatitude(),
                    hotel.getCoordinates().getLongitude(),
                    lat, lon);

            UserProximity existing = existingByHotelId.get(hotel.getId());

            if (distance <= searchRadius) {
                if (existing != null) {
                    existing.setDistanceKm(distance);
                    existing.setUpdatedAt(now);
                    toSave.add(existing);
                } else {
                    UserProximity np = new UserProximity(hotel,artist,distance);
                    np.setUpdatedAt(now);
                    toSave.add(np);
                }
            } else if (existing != null) {
                toDelete.add(existing);
            }
        }

        userProximityRepository.saveAll(toSave);
        userProximityRepository.deleteAllInBatch(toDelete);

        artist.markCoordinatesAsUnchanged();
        userRepository.save(artist);
    }

}