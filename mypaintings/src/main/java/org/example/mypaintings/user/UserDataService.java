package org.example.mypaintings.user;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.mypaintings.exception.duplicate.ConflictException;
import org.example.mypaintings.user.event.UserCreatedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserDataService {

    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public void updateUserAddress(long userId, Double latitude, Double longitude, String fullAddress) {
        log.info("Updating address and geolocation for user ID: {} (latitude: {}, longitude: {})", userId, latitude, longitude);
        User user = userRepository.getOrThrow(userId);

        boolean hasAddressChanged = !user.getFullAddress().equals(fullAddress);
        boolean hasGeolocationChanged =
                !(user.getCoordinates().getLatitude()==latitude) ||
                !(user.getCoordinates().getLongitude()==longitude);

        if (hasAddressChanged || hasGeolocationChanged)
        {
            user.changeAddress(fullAddress, latitude, longitude);
            log.debug("Successfully updated address data for user ID: {}", userId);
        }
        else {
            log.debug("New address or geolocation data for user ID: {} is identical to the current one. Skipping change.", userId);
        }

    }

    @Transactional
    public User createUser(UserRole role, String nickname, Double latitude, Double longitude, String fullAddress) {
        log.info("Starting creation of a new user with nickname: '{}' and role: {}", nickname, role);

        validateNicknameUniqueness(nickname);

        User user = new User(role, nickname, fullAddress, latitude,longitude);
        user = userRepository.saveAndFlush(user);
        log.info("Saved new user in the database with ID: {}", user.getId());

        eventPublisher.publishEvent(new UserCreatedEvent(user.getId(), user.getRole(), latitude, longitude));

        log.info("Successfully completed user registration process for ID: {}", user.getId());
        return user;
    }

    @Transactional
    public void updateUserNickname(long userId, String nickname) {
        log.info("Attempting to change nickname for user ID: {} to: '{}'", userId, nickname);

        User user = userRepository.getOrThrow(userId);
        if (!user.getNickname().equals(nickname)) {
            validateNicknameUniqueness(nickname);
            user.changeNickname(nickname);
            log.info("Successfully changed nickname for user ID: {} to: '{}'", userId, nickname);
        } else {
            log.debug("New nickname for user ID: {} is identical to the current one. Skipping change.", userId);
        }
    }

    private void validateNicknameUniqueness(String nickname) {
        if (userRepository.existsByNickname(nickname)) {
            log.warn("Conflict: Nickname '{}' is already taken in the system", nickname);
            throw new ConflictException("Nickname", nickname);
        }
    }
}
