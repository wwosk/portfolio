package org.example.mypaintings.geolocation;

import org.example.mypaintings.user.UserQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("LocationRefreshScheduler Test Suite")
class LocationRefreshSchedulerTest {

    @Mock
    private UserQueryService userQueryService;

    @Mock
    private ArtistMovementAsyncService asyncService;

    @InjectMocks
    private LocationRefreshScheduler scheduler;

    private static final double DEFAULT_RADIUS = 800.0;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(scheduler, "searchRadius", DEFAULT_RADIUS);
    }

    // ------------------------------------------------------------------
    // refreshProximityDataMultiThreaded
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("refreshProximityDataMultiThreaded")
    class RefreshProximityDataMultiThreaded {

        @Test
        @DisplayName("dispatches each artist with changed location to async processing")
        void shouldDispatchEachMovedArtistToAsync() {
            // given
            when(userQueryService.getArtistIdsWithChangedLocation())
                    .thenReturn(List.of(1L, 2L, 3L));

            // when
            scheduler.refreshProximityDataMultiThreaded();

            // then
            verify(asyncService).processAsync(1L, DEFAULT_RADIUS);
            verify(asyncService).processAsync(2L, DEFAULT_RADIUS);
            verify(asyncService).processAsync(3L, DEFAULT_RADIUS);
            verifyNoMoreInteractions(asyncService);
        }

        @Test
        @DisplayName("does nothing when no artists have changed their location")
        void shouldDoNothingWhenNoArtistsMoved() {
            // given
            when(userQueryService.getArtistIdsWithChangedLocation()).thenReturn(List.of());

            // when
            scheduler.refreshProximityDataMultiThreaded();

            // then
            verifyNoInteractions(asyncService);
        }
    }
}