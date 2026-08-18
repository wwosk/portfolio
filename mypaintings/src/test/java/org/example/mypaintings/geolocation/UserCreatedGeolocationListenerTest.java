package org.example.mypaintings.geolocation;

import org.example.mypaintings.user.UserRole;
import org.example.mypaintings.user.event.UserCreatedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserCreatedGeolocationListener Test Suite")
class UserCreatedGeolocationListenerTest {

    @Mock
    private GeolocationService geolocationService;

    @InjectMocks
    private UserCreatedGeolocationListener listener;

    private static final double DEFAULT_RADIUS = 800.0;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(listener, "searchRadius", DEFAULT_RADIUS);
    }

    // ------------------------------------------------------------------
    // onUserCreated
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("onUserCreated")
    class OnUserCreated {

        @Test
        @DisplayName("triggers full geolocation scan when created user is a HOTEL")
        void shouldTriggerScanForHotel() {
            // given
            UserCreatedEvent event = new UserCreatedEvent(42L, UserRole.HOTEL, 1.0, 1.0);

            // when
            listener.onUserCreated(event);

            // then
            verify(geolocationService).fullScanForNewHotel(42L, DEFAULT_RADIUS);
        }

        @Test
        @DisplayName("ignores event and does not trigger scan when user is not a HOTEL")
        void shouldIgnoreNonHotelUsers() {
            // given
            UserCreatedEvent event = new UserCreatedEvent(42L, UserRole.ARTIST, 1.0, 1.0);

            // when
            listener.onUserCreated(event);

            // then
            verifyNoInteractions(geolocationService);
        }
    }
}
