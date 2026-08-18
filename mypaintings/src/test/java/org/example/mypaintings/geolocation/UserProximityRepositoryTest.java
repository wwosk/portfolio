package org.example.mypaintings.geolocation;

import org.example.mypaintings.user.User;
import org.example.mypaintings.user.UserRepository;
import org.example.mypaintings.user.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@DisplayName("UserProximityRepository Test Suite")
class UserProximityRepositoryTest {

    @Autowired
    private UserProximityRepository proximityRepo;

    @Autowired
    private UserRepository userRepo;

    private User hotel1, hotel2, hotel3, artist1, artist2;

    @BeforeEach
    void setUp() {
        hotel1 = userRepo.save(new User(UserRole.HOTEL, "h1", "H1", 1.0, 1.0));
        hotel2 = userRepo.save(new User(UserRole.HOTEL, "h2", "H2", 2.0, 2.0));
        hotel3 = userRepo.save(new User(UserRole.HOTEL, "h3", "H3", 3.0, 3.0));
        artist1 = userRepo.save(new User(UserRole.ARTIST, "a1", "A1", 4.0, 4.0));
        artist2 = userRepo.save(new User(UserRole.ARTIST, "a2", "A2", 5.0, 5.0));

        proximityRepo.saveAll(List.of(
                new UserProximity(hotel1, artist1, 1.0),
                new UserProximity(hotel2, artist1, 2.0),
                new UserProximity(hotel3, artist2, 3.0)
        ));
    }

    // ------------------------------------------------------------------
    // findAllByNearbyUserAndUserIn
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("findAllByNearbyUserAndUserIn")
    class FindAllByNearbyUserAndUserIn {

        @Test
        @DisplayName("filters and returns proximities matching artist and hotel list")
        void findAllByNearbyUserAndUserInShouldFilterCorrectly() {
            // given
            List<User> hotels = List.of(hotel1, hotel2, hotel3);

            // when
            List<UserProximity> result = proximityRepo
                    .findAllByNearbyUserAndUserIn(artist1, hotels);

            // then
            assertThat(result).hasSize(2);
            assertThat(result).extracting(p -> p.getUser().getId())
                    .containsExactlyInAnyOrder(hotel1.getId(), hotel2.getId());
        }

        @Test
        @DisplayName("returns empty list when artist has no proximities with given hotels")
        void findAllByNearbyUserAndUserInShouldReturnEmptyForUnrelatedArtist() {
            // given
            List<User> hotels = List.of(hotel1, hotel2);

            // when
            List<UserProximity> result = proximityRepo
                    .findAllByNearbyUserAndUserIn(artist2, hotels);

            // then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("returns empty list when hotel collection is empty")
        void findAllByNearbyUserAndUserInShouldReturnEmptyForEmptyCollection() {
            // when
            List<UserProximity> result = proximityRepo
                    .findAllByNearbyUserAndUserIn(artist1, Collections.emptyList());

            // then
            assertThat(result).isEmpty();
        }
    }

    // ------------------------------------------------------------------
    // findAllByUserAndNearbyUserIn
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("findAllByUserAndNearbyUserIn")
    class FindAllByUserAndNearbyUserIn {

        @Test
        @DisplayName("filters and returns proximities matching hotel and artist list")
        void findAllByUserAndNearbyUserInShouldFilterCorrectly() {
            // given
            List<User> artists = List.of(artist1, artist2);

            // when
            List<UserProximity> result = proximityRepo
                    .findAllByUserAndNearbyUserIn(hotel1, artists);

            // then
            assertThat(result).hasSize(1);
            assertThat(result.getFirst().getNearbyUser().getId()).isEqualTo(artist1.getId());
        }

        @Test
        @DisplayName("returns empty list when no match is found for hotel and artists")
        void findAllByUserAndNearbyUserInShouldReturnEmptyWhenNoMatch() {
            // given
            List<User> artists = List.of(artist1);

            // when
            List<UserProximity> result = proximityRepo
                    .findAllByUserAndNearbyUserIn(hotel3, artists);

            // then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("returns empty list when artist collection is empty")
        void findAllByUserAndNearbyUserInShouldReturnEmptyForEmptyCollection() {
            // when
            List<UserProximity> result = proximityRepo
                    .findAllByUserAndNearbyUserIn(hotel1, Collections.emptyList());

            // then
            assertThat(result).isEmpty();
        }
    }
}