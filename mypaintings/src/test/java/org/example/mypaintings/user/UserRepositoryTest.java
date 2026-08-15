package org.example.mypaintings.user;

import org.example.mypaintings.exception.notfound.ResourceNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@ActiveProfiles("test")
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    // ------------------------------------------------------------------
    // existsByNickname
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("existsByNickname")
    class ExistsByNickname {

        @Test
        @DisplayName("returns true when nickname exists")
        void returnsTrueWhenExists() {
            // given
            saveUser(UserRole.ARTIST, "artist1", 50.0, 19.0);

            // when
            boolean exists = userRepository.existsByNickname("artist1");

            // then
            assertThat(exists).isTrue();
        }

        @Test
        @DisplayName("returns false when nickname does not exist")
        void returnsFalseWhenMissing() {
            // given
            saveUser(UserRole.ARTIST, "artist1", 50.0, 19.0);

            // when
            boolean exists = userRepository.existsByNickname("nobody");

            // then
            assertThat(exists).isFalse();
        }

        @Test
        @DisplayName("is case-sensitive (nickname is stored as-is)")
        void isCaseSensitive() {
            // given
            saveUser(UserRole.ARTIST, "Artist1", 50.0, 19.0);

            // when & then
            assertThat(userRepository.existsByNickname("artist1")).isFalse();
            assertThat(userRepository.existsByNickname("Artist1")).isTrue();
        }

        @Test
        @DisplayName("returns false when nickname is null or empty")
        void returnsFalseForNullOrEmpty() {
            // given
            saveUser(UserRole.ARTIST, "artist1", 50.0, 19.0);

            // when & then
            assertThat(userRepository.existsByNickname(null)).isFalse();
            assertThat(userRepository.existsByNickname("")).isFalse();
        }
    }

    // ------------------------------------------------------------------
    // getOrThrow (default method)
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("getOrThrow")
    class GetOrThrow {

        @Test
        @DisplayName("returns user when present")
        void returnsUserWhenPresent() {
            // given
            User user = saveUser(UserRole.ARTIST, "artist1", 50.0, 19.0);

            // when
            User result = userRepository.getOrThrow(user.getId());

            // then
            assertThat(result.getId()).isEqualTo(user.getId());
            assertThat(result.getNickname()).isEqualTo("artist1");
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when missing")
        void throwsWhenMissing() {
            // given
            Long missingId = 999_999L;

            // when & then
            assertThatThrownBy(() -> userRepository.getOrThrow(missingId))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ------------------------------------------------------------------
    // findArtistsInArea
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("findArtistsInArea")
    class FindArtistsInArea {

        @Test
        @DisplayName("returns artists inside bounding box")
        void returnsArtistsInsideBox() {
            // given
            User inside = saveUser(UserRole.ARTIST, "inside", 50.05, 19.95);
            saveUser(UserRole.ARTIST, "outside", 40.0, 10.0);

            // when
            List<User> result = userRepository.findArtistsInArea(50.0, 50.1, 19.9, 20.0);

            // then
            assertThat(result).extracting(User::getId).containsExactly(inside.getId());
        }

        @Test
        @DisplayName("returns multiple artists inside bounding box")
        void returnsMultipleArtistsInsideBox() {
            // given
            User artist1 = saveUser(UserRole.ARTIST, "artist1", 50.02, 19.92);
            User artist2 = saveUser(UserRole.ARTIST, "artist2", 50.08, 19.98);
            saveUser(UserRole.ARTIST, "outside", 10.0, 10.0);

            // when
            List<User> result = userRepository.findArtistsInArea(50.0, 50.1, 19.9, 20.0);

            // then
            assertThat(result).extracting(User::getId)
                    .containsExactlyInAnyOrder(artist1.getId(), artist2.getId());
        }

        @Test
        @DisplayName("excludes hotels even if inside the box")
        void excludesHotels() {
            // given
            saveUser(UserRole.ARTIST, "artist-in", 50.05, 19.95);
            User hotel = saveUser(UserRole.HOTEL, "hotel-in", 50.05, 19.95);

            // when
            List<User> result = userRepository.findArtistsInArea(50.0, 50.1, 19.9, 20.0);

            // then
            assertThat(result).extracting(User::getId).doesNotContain(hotel.getId());
        }

        @Test
        @DisplayName("includes users exactly on the boundary (BETWEEN is inclusive)")
        void includesBoundaryValues() {
            // given
            User onBoundary = saveUser(UserRole.ARTIST, "on-boundary", 50.0, 19.9);

            // when
            List<User> result = userRepository.findArtistsInArea(50.0, 50.1, 19.9, 20.0);

            // then
            assertThat(result).extracting(User::getId).containsExactly(onBoundary.getId());
        }

        @Test
        @DisplayName("returns artists with negative coordinates in southern and western hemisphere")
        void returnsArtistsWithNegativeCoordinates() {
            // given
            User negativeAreaUser = saveUser(UserRole.ARTIST, "negative-coords", -22.90, -43.17);

            // when
            List<User> result = userRepository.findArtistsInArea(-23.0, -22.0, -44.0, -43.0);

            // then
            assertThat(result).extracting(User::getId).containsExactly(negativeAreaUser.getId());
        }

        @Test
        @DisplayName("returns empty list when min value is greater than max value")
        void returnsEmptyWhenMinGreaterThanMax() {
            // given
            saveUser(UserRole.ARTIST, "artist", 50.05, 19.95);

            // when
            List<User> result = userRepository.findArtistsInArea(50.1, 50.0, 20.0, 19.9);

            // then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("returns empty list when no artists in area")
        void returnsEmptyWhenNoneInArea() {
            // given
            saveUser(UserRole.ARTIST, "far-away", 10.0, 10.0);

            // when
            List<User> result = userRepository.findArtistsInArea(50.0, 50.1, 19.9, 20.0);

            // then
            assertThat(result).isEmpty();
        }
    }

    // ------------------------------------------------------------------
    // findHotelsInArea
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("findHotelsInArea")
    class FindHotelsInArea {

        @Test
        @DisplayName("returns hotels inside bounding box")
        void returnsHotelsInsideBox() {
            // given
            User inside = saveUser(UserRole.HOTEL, "hotel-in", 50.05, 19.95);
            saveUser(UserRole.HOTEL, "hotel-out", 40.0, 10.0);

            // when
            List<User> result = userRepository.findHotelsInArea(50.0, 50.1, 19.9, 20.0);

            // then
            assertThat(result).extracting(User::getId).containsExactly(inside.getId());
        }

        @Test
        @DisplayName("excludes artists even if inside the box")
        void excludesArtists() {
            // given
            User artist = saveUser(UserRole.ARTIST, "artist-in", 50.05, 19.95);
            saveUser(UserRole.HOTEL, "hotel-in", 50.05, 19.95);

            // when
            List<User> result = userRepository.findHotelsInArea(50.0, 50.1, 19.9, 20.0);

            // then
            assertThat(result).extracting(User::getId).doesNotContain(artist.getId());
        }

        @Test
        @DisplayName("returns hotels with negative coordinates")
        void returnsHotelsWithNegativeCoordinates() {
            // given
            User negativeHotel = saveUser(UserRole.HOTEL, "hotel-negative", -33.86, -151.20);

            // when
            List<User> result = userRepository.findHotelsInArea(-34.0, -33.0, -152.0, -150.0);

            // then
            assertThat(result).extracting(User::getId).containsExactly(negativeHotel.getId());
        }

        @Test
        @DisplayName("returns empty list when no hotels in area")
        void returnsEmptyWhenNoneInArea() {
            // given
            saveUser(UserRole.HOTEL, "far-away", 10.0, 10.0);

            // when
            List<User> result = userRepository.findHotelsInArea(50.0, 50.1, 19.9, 20.0);

            // then
            assertThat(result).isEmpty();
        }
    }

    // ------------------------------------------------------------------
    // findByRoleAndCoordinatesLocationChangedTrue
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("findByRoleAndCoordinatesLocationChangedTrue")
    class FindByRoleAndLocationChanged {

        @Test
        @DisplayName("returns only artists whose location changed")
        void returnsOnlyArtistsWithLocationChanged() {
            // given
            User changed = saveUser(UserRole.ARTIST, "changed", 50.0, 19.0);
            User unchanged = saveUnchangedUser(UserRole.ARTIST, "unchanged", 50.0, 19.0);
            User hotel = saveUser(UserRole.HOTEL, "hotel-changed", 50.0, 19.0);

            // when
            List<User> result = userRepository
                    .findByRoleAndCoordinatesLocationChangedTrue(UserRole.ARTIST);

            // then
            assertThat(result).extracting(User::getId)
                    .containsExactly(changed.getId())
                    .doesNotContain(unchanged.getId(), hotel.getId());
        }

        @Test
        @DisplayName("returns only hotels whose location changed")
        void returnsOnlyHotelsWithLocationChanged() {
            // given
            User changedHotel = saveUser(UserRole.HOTEL, "hotel-changed", 50.0, 19.0);
            saveUnchangedUser(UserRole.HOTEL, "hotel-unchanged", 50.0, 19.0);
            saveUser(UserRole.ARTIST, "artist-changed", 50.0, 19.0);

            // when
            List<User> result = userRepository
                    .findByRoleAndCoordinatesLocationChangedTrue(UserRole.HOTEL);

            // then
            assertThat(result)
                    .extracting(User::getId)
                    .contains(changedHotel.getId());
        }

        @Test
        @DisplayName("returns empty list when nobody has changed location for given role")
        void returnsEmptyWhenNoneChanged() {
            // given
            saveUnchangedUser(UserRole.ARTIST, "unchanged", 50.0, 19.0);

            // when
            List<User> result = userRepository
                    .findByRoleAndCoordinatesLocationChangedTrue(UserRole.ARTIST);

            // then
            assertThat(result).isEmpty();
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private User saveUser(UserRole role, String nickname, double lat, double lon) {
        return userRepository.saveAndFlush(
                new User(role, nickname, "Addr " + nickname, lat, lon));
    }

    private User saveUnchangedUser(UserRole role, String nickname, double lat, double lon) {
        User user = new User(role, nickname, "Addr " + nickname, lat, lon);
        user.markCoordinatesAsUnchanged();
        return userRepository.saveAndFlush(user);
    }
}