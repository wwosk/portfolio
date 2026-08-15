package org.example.mypaintings.user;

import lombok.RequiredArgsConstructor;
import org.example.mypaintings.exception.validation.BusinessValidationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;


@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserQueryService {

    private final UserRepository userRepository;

    public User getValidHotelOrThrow(long userId) {
        User user = userRepository.getOrThrow(userId);
        user.validateRoleOrThrow(UserRole.HOTEL);
        return user;
    }

    public List<User> getHotelsInArea(double minLat, double maxLat, double minLon, double maxLon) {
        validateAreaCoordinates(minLat, maxLat, minLon, maxLon);
        return userRepository.findHotelsInArea(minLat, maxLat, minLon, maxLon);
    }


    public List<User> getArtistsInArea(double minLat, double maxLat, double minLon, double maxLon) {
        validateAreaCoordinates(minLat, maxLat, minLon, maxLon);
        return userRepository.findArtistsInArea(minLat, maxLat, minLon, maxLon);
    }

    public List<Long> getArtistIdsWithChangedLocation() {
        return userRepository
                .findByRoleAndCoordinatesLocationChangedTrue(UserRole.ARTIST)
                .stream()
                .map(User::getId)
                .collect(Collectors.toList());
    }

    public User getValidArtistOrThrow(long userId) {
        User user = userRepository.getOrThrow(userId);
        user.validateRoleOrThrow(UserRole.ARTIST);
        return user;
    }


    public UserRole getUserRole(long userId) {
        return userRepository.getOrThrow(userId).getRole();
    }

    public User getUserReference(long userId) {
        return userRepository.getReferenceById(userId);
    }

    private void validateAreaCoordinates(double minLat, double maxLat, double minLon, double maxLon) {
        if (minLat > maxLat || minLon > maxLon) {
            throw new BusinessValidationException("Minimum coordinates cannot be greater than maximum coordinates");
        }
        if (minLat < -90 || maxLat > 90 || minLon < -180 || maxLon > 180) {
            throw new BusinessValidationException("Coordinates are out of valid geographical bounds");
        }
    }


}
