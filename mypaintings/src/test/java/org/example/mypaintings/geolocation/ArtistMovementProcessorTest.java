package org.example.mypaintings.geolocation;

import org.example.mypaintings.user.User;
import org.example.mypaintings.user.UserQueryService;
import org.example.mypaintings.user.UserRepository;
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

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ArtistMovementProcessor Test Suite")
class ArtistMovementProcessorTest {

    @Mock
    private UserQueryService userQueryService;

    @Mock
    private UserProximityRepository userProximityRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private ArtistMovementProcessor processor;

    private User artist;
    private User hotel;

    @BeforeEach
    void setUp() {
        artist = new User(UserRole.ARTIST, "artist", "A", 10.0, 10.0);
        setId(artist, 1L);
        hotel = new User(UserRole.HOTEL, "hotel", "H", 10.01, 10.01); // ~1.5 km
        setId(hotel, 2L);
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
    // processArtistMovement
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("processArtistMovement")
    class ProcessArtistMovement {

        @Test
        @DisplayName("does nothing when artist is not found in repository")
        void shouldDoNothingWhenArtistNotFound() {
            // given
            when(userRepository.findById(99L)).thenReturn(Optional.empty());

            // when
            processor.processArtistMovement(99L, 100);

            // then
            verifyNoInteractions(userQueryService, userProximityRepository);
            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("does nothing when artist location has not changed")
        void shouldDoNothingWhenLocationNotChanged() {
            // given
            artist.markCoordinatesAsUnchanged();
            when(userRepository.findById(1L)).thenReturn(Optional.of(artist));

            // when
            processor.processArtistMovement(1L, 100);

            // then
            verifyNoInteractions(userQueryService, userProximityRepository);
        }

        @Test
        @DisplayName("creates new proximity record when hotel is within search radius and marks location unchanged")
        void shouldCreateNewProximityWhenHotelWithinRadius() {
            // given
            when(userRepository.findById(1L)).thenReturn(Optional.of(artist));
            when(userQueryService.getHotelsInArea(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                    .thenReturn(List.of(hotel));
            when(userProximityRepository.findAllByNearbyUserAndUserIn(eq(artist), anyList()))
                    .thenReturn(List.of());

            // when
            processor.processArtistMovement(1L, 100);

            // then
            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<UserProximity>> saveCaptor = ArgumentCaptor.forClass(List.class);
            verify(userProximityRepository).saveAll(saveCaptor.capture());

            assertThat(saveCaptor.getValue()).hasSize(1);
            UserProximity saved = saveCaptor.getValue().getFirst();
            assertThat(saved.getUser()).isSameAs(hotel);
            assertThat(saved.getNearbyUser()).isSameAs(artist);
            assertThat(saved.getDistanceKm()).isLessThanOrEqualTo(100);

            verify(userProximityRepository).deleteAllInBatch(anyList());
            verify(userRepository).save(artist);
            assertThat(artist.getCoordinates().isLocationChanged()).isFalse();
        }

        @Test
        @DisplayName("updates existing proximity record when hotel is within search radius")
        void shouldUpdateExistingProximityWithinRadius() {
            // given
            UserProximity existing = new UserProximity(hotel, artist, 50.0);

            when(userRepository.findById(1L)).thenReturn(Optional.of(artist));
            when(userQueryService.getHotelsInArea(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                    .thenReturn(List.of(hotel));
            when(userProximityRepository.findAllByNearbyUserAndUserIn(eq(artist), anyList()))
                    .thenReturn(List.of(existing));

            // when
            processor.processArtistMovement(1L, 100);

            // then
            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<UserProximity>> saveCaptor = ArgumentCaptor.forClass(List.class);
            verify(userProximityRepository).saveAll(saveCaptor.capture());

            assertThat(saveCaptor.getValue()).hasSize(1);
            assertThat(saveCaptor.getValue().getFirst().getDistanceKm()).isNotEqualTo(50.0);
            verify(userProximityRepository).deleteAllInBatch(argThat((List<UserProximity> list) -> list.isEmpty()));
        }

        @Test
        @DisplayName("deletes existing proximity record when hotel is outside search radius")
        void shouldDeleteExistingProximityWhenHotelOutsideRadius() {
            // given
            UserProximity existing = new UserProximity(hotel, artist, 50.0);

            when(userRepository.findById(1L)).thenReturn(Optional.of(artist));
            when(userQueryService.getHotelsInArea(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                    .thenReturn(List.of(hotel));
            when(userProximityRepository.findAllByNearbyUserAndUserIn(eq(artist), anyList()))
                    .thenReturn(List.of(existing));

            // when - using tiny radius so hotel is out of range
            processor.processArtistMovement(1L, 0.001);

            // then
            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<UserProximity>> deleteCaptor = ArgumentCaptor.forClass(List.class);
            verify(userProximityRepository).deleteAllInBatch(deleteCaptor.capture());
            assertThat(deleteCaptor.getValue()).containsExactly(existing);

            verify(userProximityRepository).saveAll(argThat((List<UserProximity> list) -> list.isEmpty()));
            verify(userRepository).save(artist);
        }
    }
}
