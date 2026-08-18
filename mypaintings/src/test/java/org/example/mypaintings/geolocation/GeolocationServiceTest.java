package org.example.mypaintings.geolocation;

import org.example.mypaintings.user.User;
import org.example.mypaintings.user.UserQueryService;
import org.example.mypaintings.user.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("GeolocationService Test Suite")
class GeolocationServiceTest {

    @Mock
    private UserProximityRepository userProximityRepository;

    @Mock
    private UserQueryService userQueryService;

    @InjectMocks
    private GeolocationService service;

    private User hotel;
    private User artist;

    @BeforeEach
    void setUp() {
        hotel = new User(UserRole.HOTEL, "hotel", "H", 10.0, 10.0);
        setId(hotel, 1L);
        artist = new User(UserRole.ARTIST, "artist", "A", 10.01, 10.01);
        setId(artist, 2L);
        ReflectionTestUtils.setField(service, "searchRadius", 800.0);
    }

    private static void setId(User u, long id) {
        try {
            var f = User.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(u, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // ------------------------------------------------------------------
    // fullScanForNewHotel
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("fullScanForNewHotel")
    class FullScanForNewHotel {

        @Test
        @DisplayName("creates new proximity record when nearby artist is found")
        void fullScanShouldCreateNewProximityForNearbyArtist() {
            // given
            when(userQueryService.getValidHotelOrThrow(1L)).thenReturn(hotel);
            when(userQueryService.getArtistsInArea(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                    .thenReturn(List.of(artist));
            when(userProximityRepository.findAllByUserAndNearbyUserIn(eq(hotel), anyList()))
                    .thenReturn(List.of());

            // when
            service.fullScanForNewHotel(1L, 800);

            // then
            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<UserProximity>> saveCaptor = ArgumentCaptor.forClass(List.class);
            verify(userProximityRepository).saveAll(saveCaptor.capture());
            assertThat(saveCaptor.getValue()).hasSize(1);
            assertThat(saveCaptor.getValue().getFirst().getUser()).isSameAs(hotel);
            assertThat(saveCaptor.getValue().getFirst().getNearbyUser()).isSameAs(artist);
            verify(userProximityRepository).deleteAllInBatch(argThat((List<UserProximity> list) -> list.isEmpty()));
        }

        @Test
        @DisplayName("updates distance and timestamp for existing proximity record")
        void fullScanShouldUpdateExisting() {
            // given
            UserProximity existing = new UserProximity(hotel, artist, 999.0);
            when(userQueryService.getValidHotelOrThrow(1L)).thenReturn(hotel);
            when(userQueryService.getArtistsInArea(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                    .thenReturn(List.of(artist));
            when(userProximityRepository.findAllByUserAndNearbyUserIn(eq(hotel), anyList()))
                    .thenReturn(List.of(existing));

            // when
            service.fullScanForNewHotel(1L, 800);

            // then
            verify(userProximityRepository).saveAll(argThat(list -> {
                @SuppressWarnings("unchecked")
                List<UserProximity> l = (List<UserProximity>) list;
                return l.size() == 1 && l.getFirst().getDistanceKm() != 999.0;
            }));
        }

        @Test
        @DisplayName("deletes existing proximity record when artist moves outside search radius")
        void fullScanShouldDeleteExistingWhenOutsideRadius() {
            // given
            UserProximity existing = new UserProximity(hotel, artist, 5.0);
            when(userQueryService.getValidHotelOrThrow(1L)).thenReturn(hotel);
            when(userQueryService.getArtistsInArea(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                    .thenReturn(List.of(artist));
            when(userProximityRepository.findAllByUserAndNearbyUserIn(eq(hotel), anyList()))
                    .thenReturn(List.of(existing));

            // when
            service.fullScanForNewHotel(1L, 0.001);

            // then
            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<UserProximity>> deleteCaptor = ArgumentCaptor.forClass(List.class);
            verify(userProximityRepository).deleteAllInBatch(deleteCaptor.capture());
            assertThat(deleteCaptor.getValue()).containsExactly(existing);
        }
    }

    // ------------------------------------------------------------------
    // validateSearchRadius
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("validateSearchRadius")
    class ValidateSearchRadius {

        @Test
        @DisplayName("throws exception when search radius is out of valid range (<= 0 or > 20000)")
        void validateSearchRadiusShouldThrowOnInvalidValue() {
            // given & when & then
            ReflectionTestUtils.setField(service, "searchRadius", 0.0);
            assertThatThrownBy(() -> service.validateSearchRadius())
                    .isInstanceOf(IllegalStateException.class);

            ReflectionTestUtils.setField(service, "searchRadius", 20001.0);
            assertThatThrownBy(() -> service.validateSearchRadius())
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("passes successfully when search radius is within valid range")
        void validateSearchRadiusShouldPassOnValidValue() {
            // given
            ReflectionTestUtils.setField(service, "searchRadius", 500.0);

            // when & then
            assertThatCode(() -> service.validateSearchRadius()).doesNotThrowAnyException();
        }
    }
}