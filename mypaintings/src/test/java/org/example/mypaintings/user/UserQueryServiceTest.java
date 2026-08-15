package org.example.mypaintings.user;

import org.example.mypaintings.exception.access.MissingRequiredRoleException;
import org.example.mypaintings.exception.notfound.ResourceNotFoundException;
import org.example.mypaintings.exception.validation.BusinessValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserQueryService Test Suite")
class UserQueryServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserQueryService userQueryService;

    private User hotelUser;
    private User artistUser;

    @BeforeEach
    void setUp() {
        hotelUser = new User(UserRole.HOTEL, "hotel123", "Main St 1", 52.0, 21.0);
        artistUser = new User(UserRole.ARTIST, "artist123", "Art St 2", 50.0, 19.0);
    }

    // ------------------------------------------------------------------
    // getValidHotelOrThrow
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("getValidHotelOrThrow")
    class GetValidHotelOrThrow {

        @Test
        @DisplayName("returns hotel user when role is HOTEL")
        void returnsHotelWhenRoleMatches() {
            // given
            when(userRepository.getOrThrow(1L)).thenReturn(hotelUser);

            // when
            User result = userQueryService.getValidHotelOrThrow(1L);

            // then
            assertThat(result).isEqualTo(hotelUser);
            verify(userRepository).getOrThrow(1L);
        }

        @Test
        @DisplayName("throws MissingRequiredRoleException when user is not HOTEL")
        void throwsWhenUserIsNotHotel() {
            // given
            when(userRepository.getOrThrow(2L)).thenReturn(artistUser);

            // when & then
            assertThatThrownBy(() -> userQueryService.getValidHotelOrThrow(2L))
                    .isInstanceOf(MissingRequiredRoleException.class);
        }

        @Test
        @DisplayName("propagates ResourceNotFoundException when user does not exist")
        void propagatesResourceNotFoundExceptionWhenUserMissing() {
            // given
            when(userRepository.getOrThrow(99L)).thenThrow(new ResourceNotFoundException(User.class, 99L));

            // when & then
            assertThatThrownBy(() -> userQueryService.getValidHotelOrThrow(99L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ------------------------------------------------------------------
    // getValidArtistOrThrow
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("getValidArtistOrThrow")
    class GetValidArtistOrThrow {

        @Test
        @DisplayName("returns artist user when role is ARTIST")
        void returnsArtistWhenRoleMatches() {
            // given
            when(userRepository.getOrThrow(2L)).thenReturn(artistUser);

            // when
            User result = userQueryService.getValidArtistOrThrow(2L);

            // then
            assertThat(result).isEqualTo(artistUser);
            verify(userRepository).getOrThrow(2L);
        }

        @Test
        @DisplayName("throws MissingRequiredRoleException when user is not ARTIST")
        void throwsWhenUserIsNotArtist() {
            // given
            when(userRepository.getOrThrow(1L)).thenReturn(hotelUser);

            // when & then
            assertThatThrownBy(() -> userQueryService.getValidArtistOrThrow(1L))
                    .isInstanceOf(MissingRequiredRoleException.class);
        }

        @Test
        @DisplayName("propagates ResourceNotFoundException when user does not exist")
        void propagatesResourceNotFoundExceptionWhenUserMissing() {
            // given
            when(userRepository.getOrThrow(99L)).thenThrow(new ResourceNotFoundException(User.class, 99L));

            // when & then
            assertThatThrownBy(() -> userQueryService.getValidArtistOrThrow(99L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ------------------------------------------------------------------
    // getHotelsInArea
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("getHotelsInArea")
    class GetHotelsInArea {

        @Test
        @DisplayName("returns hotels from repository for valid bounding box")
        void returnsHotelsFromRepository() {
            // given
            double minLat = 50.0, maxLat = 53.0, minLon = 18.0, maxLon = 22.0;
            when(userRepository.findHotelsInArea(minLat, maxLat, minLon, maxLon))
                    .thenReturn(List.of(hotelUser));

            // when
            List<User> result = userQueryService.getHotelsInArea(minLat, maxLat, minLon, maxLon);

            // then
            assertThat(result).containsExactly(hotelUser);
            verify(userRepository).findHotelsInArea(minLat, maxLat, minLon, maxLon);
        }

        @Test
        @DisplayName("throws BusinessValidationException when min coordinate is greater than max coordinate")
        void throwsWhenMinGreaterThanMax() {
            // given
            double minLat = 55.0, maxLat = 50.0, minLon = 18.0, maxLon = 22.0;

            // when & then
            assertThatThrownBy(() -> userQueryService.getHotelsInArea(minLat, maxLat, minLon, maxLon))
                    .isInstanceOf(BusinessValidationException.class);
            verifyNoInteractions(userRepository);
        }

        @Test
        @DisplayName("throws BusinessValidationException when coordinates are out of bounds")
        void throwsWhenOutOfBounds() {
            // given
            double minLat = -100.0, maxLat = 50.0, minLon = 18.0, maxLon = 22.0;

            // when & then
            assertThatThrownBy(() -> userQueryService.getHotelsInArea(minLat, maxLat, minLon, maxLon))
                    .isInstanceOf(BusinessValidationException.class);
            verifyNoInteractions(userRepository);
        }
    }

    // ------------------------------------------------------------------
    // getArtistsInArea
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("getArtistsInArea")
    class GetArtistsInArea {

        @Test
        @DisplayName("returns artists from repository for valid bounding box")
        void returnsArtistsFromRepository() {
            // given
            double minLat = 49.0, maxLat = 51.0, minLon = 18.0, maxLon = 20.0;
            when(userRepository.findArtistsInArea(minLat, maxLat, minLon, maxLon))
                    .thenReturn(List.of(artistUser));

            // when
            List<User> result = userQueryService.getArtistsInArea(minLat, maxLat, minLon, maxLon);

            // then
            assertThat(result).containsExactly(artistUser);
            verify(userRepository).findArtistsInArea(minLat, maxLat, minLon, maxLon);
        }

        @Test
        @DisplayName("throws BusinessValidationException when min coordinate is greater than max coordinate")
        void throwsWhenMinGreaterThanMax() {
            // given
            double minLat = 49.0, maxLat = 51.0, minLon = 25.0, maxLon = 20.0;

            // when & then
            assertThatThrownBy(() -> userQueryService.getArtistsInArea(minLat, maxLat, minLon, maxLon))
                    .isInstanceOf(BusinessValidationException.class);
            verifyNoInteractions(userRepository);
        }
    }

    // ------------------------------------------------------------------
    // getUserReference
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("getUserReference")
    class GetUserReference {

        @Test
        @DisplayName("returns entity reference from repository")
        void returnsReferenceFromRepository() {
            // given
            User reference = mock(User.class);
            when(userRepository.getReferenceById(5L)).thenReturn(reference);

            // when
            User result = userQueryService.getUserReference(5L);

            // then
            assertThat(result).isSameAs(reference);
            verify(userRepository).getReferenceById(5L);
        }
    }

    // ------------------------------------------------------------------
    // getUserRole
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("getUserRole")
    class GetUserRole {

        @Test
        @DisplayName("returns HOTEL role for hotel user")
        void returnsHotelRole() {
            // given
            when(userRepository.getOrThrow(1L)).thenReturn(hotelUser);

            // when
            UserRole result = userQueryService.getUserRole(1L);

            // then
            assertThat(result).isEqualTo(UserRole.HOTEL);
            verify(userRepository).getOrThrow(1L);
        }

        @Test
        @DisplayName("returns ARTIST role for artist user")
        void returnsArtistRole() {
            // given
            when(userRepository.getOrThrow(2L)).thenReturn(artistUser);

            // when
            UserRole result = userQueryService.getUserRole(2L);

            // then
            assertThat(result).isEqualTo(UserRole.ARTIST);
            verify(userRepository).getOrThrow(2L);
        }
    }

    // ------------------------------------------------------------------
    // getArtistIdsWithChangedLocation
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("getArtistIdsWithChangedLocation")
    class GetArtistIdsWithChangedLocation {

        @Test
        @DisplayName("returns list of artist IDs with changed location")
        void returnsMappedIds() {
            // given
            User artist1 = mock(User.class);
            User artist2 = mock(User.class);
            when(artist1.getId()).thenReturn(10L);
            when(artist2.getId()).thenReturn(20L);
            when(userRepository.findByRoleAndCoordinatesLocationChangedTrue(UserRole.ARTIST))
                    .thenReturn(List.of(artist1, artist2));

            // when
            List<Long> result = userQueryService.getArtistIdsWithChangedLocation();

            // then
            assertThat(result).containsExactly(10L, 20L);
            verify(userRepository).findByRoleAndCoordinatesLocationChangedTrue(UserRole.ARTIST);
        }

        @Test
        @DisplayName("returns empty list when no artists have changed location")
        void returnsEmptyListWhenNoArtists() {
            // given
            when(userRepository.findByRoleAndCoordinatesLocationChangedTrue(UserRole.ARTIST))
                    .thenReturn(List.of());

            // when
            List<Long> result = userQueryService.getArtistIdsWithChangedLocation();

            // then
            assertThat(result).isEmpty();
            verify(userRepository).findByRoleAndCoordinatesLocationChangedTrue(UserRole.ARTIST);
        }
    }
}