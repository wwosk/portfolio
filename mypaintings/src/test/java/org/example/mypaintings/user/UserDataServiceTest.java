package org.example.mypaintings.user;

import org.example.mypaintings.exception.duplicate.ConflictException;
import org.example.mypaintings.user.event.UserCreatedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserDataService Test Suite")
class UserDataServiceTest {

    private static final long USER_ID = 1L;
    private static final String VALID_NICKNAME = "vanGogh";
    private static final String VALID_ADDRESS = "Museum Street 1, Amsterdam";
    private static final double VALID_LATITUDE = 52.3676;
    private static final double VALID_LONGITUDE = 4.9041;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private UserDataService userDataService;

    // ---------------------------------------------------------------------
    // Helper: simulates ID assignment by database via reflection
    // ---------------------------------------------------------------------
    private static User withId(User user, Long id) {
        try {
            var field = User.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(user, id);
            return user;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot set id on User via reflection", e);
        }
    }

    // ---------------------------------------------------------------------
    // createUser
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("createUser")
    class CreateUser {

        @Test
        @DisplayName("creates user successfully when nickname is unique and publishes event")
        void createUser_shouldCreateUserAndPublishEvent_whenNicknameIsUnique() {
            // given
            UserRole role = UserRole.ARTIST;
            when(userRepository.existsByNickname(VALID_NICKNAME)).thenReturn(false);
            when(userRepository.saveAndFlush(any(User.class)))
                    .thenAnswer(invocation -> withId(invocation.getArgument(0), USER_ID));

            // when
            User created = userDataService.createUser(role, VALID_NICKNAME, VALID_LATITUDE, VALID_LONGITUDE, VALID_ADDRESS);

            // then
            assertThat(created.getId()).isEqualTo(USER_ID);
            assertThat(created.getRole()).isEqualTo(role);
            assertThat(created.getNickname()).isEqualTo(VALID_NICKNAME);
            assertThat(created.getFullAddress()).isEqualTo(VALID_ADDRESS);
            assertThat(created.getCoordinates().getLatitude()).isEqualTo(VALID_LATITUDE);
            assertThat(created.getCoordinates().getLongitude()).isEqualTo(VALID_LONGITUDE);

            verify(userRepository).existsByNickname(VALID_NICKNAME);
            verify(userRepository).saveAndFlush(any(User.class));

            // Poprawione jawne rzutowanie typu w argThat:
            verify(eventPublisher).publishEvent(argThat((UserCreatedEvent event) ->
                    event.userId()==USER_ID &&
                            event.role().equals(role)
            ));
        }

        @Test
        @DisplayName("throws ConflictException and skips event publishing when nickname already exists")
        void createUser_shouldThrow_whenNicknameExists() {
            // given
            when(userRepository.existsByNickname(VALID_NICKNAME)).thenReturn(true);

            // when & then
            assertThatThrownBy(() -> userDataService.createUser(
                    UserRole.ARTIST, VALID_NICKNAME, VALID_LATITUDE, VALID_LONGITUDE, VALID_ADDRESS))
                    .isInstanceOf(ConflictException.class);

            verify(userRepository).existsByNickname(VALID_NICKNAME);
            verify(userRepository, never()).saveAndFlush(any(User.class));
            verifyNoInteractions(eventPublisher);
        }
    }

    // ---------------------------------------------------------------------
    // updateUserAddress
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("updateUserAddress")
    class UpdateUserAddress {

        @Test
        @DisplayName("changes address and coordinates when data differs")
        void updateUserAddress_shouldChangeAddress_whenDataDiffers() {
            // given
            User user = new User(UserRole.ARTIST, VALID_NICKNAME, VALID_ADDRESS, VALID_LATITUDE, VALID_LONGITUDE);
            String newAddress = "New Street 2, Paris";
            double newLatitude = 48.8566;
            double newLongitude = 2.3522;
            when(userRepository.getOrThrow(USER_ID)).thenReturn(user);

            // when
            userDataService.updateUserAddress(USER_ID, newLatitude, newLongitude, newAddress);

            // then
            assertThat(user.getFullAddress()).isEqualTo(newAddress);
            assertThat(user.getCoordinates().getLatitude()).isEqualTo(newLatitude);
            assertThat(user.getCoordinates().getLongitude()).isEqualTo(newLongitude);
            verify(userRepository).getOrThrow(USER_ID);
        }

        @Test
        @DisplayName("skips update when address and coordinates are identical")
        void updateUserAddress_shouldSkip_whenDataIdentical() {
            // given
            User user = new User(UserRole.ARTIST, VALID_NICKNAME, VALID_ADDRESS, VALID_LATITUDE, VALID_LONGITUDE);
            when(userRepository.getOrThrow(USER_ID)).thenReturn(user);

            // when
            userDataService.updateUserAddress(USER_ID, VALID_LATITUDE, VALID_LONGITUDE, VALID_ADDRESS);

            // then
            assertThat(user.getFullAddress()).isEqualTo(VALID_ADDRESS);
            assertThat(user.getCoordinates().getLatitude()).isEqualTo(VALID_LATITUDE);
            assertThat(user.getCoordinates().getLongitude()).isEqualTo(VALID_LONGITUDE);
            verify(userRepository).getOrThrow(USER_ID);
        }

        @Test
        @DisplayName("throws IllegalStateException when hotel tries to change address")
        void updateUserAddress_shouldThrow_whenHotelTriesToChangeAddress() {
            // given
            User hotelUser = new User(UserRole.HOTEL, VALID_NICKNAME, VALID_ADDRESS, VALID_LATITUDE, VALID_LONGITUDE);
            when(userRepository.getOrThrow(USER_ID)).thenReturn(hotelUser);

            // when & then
            assertThatThrownBy(() -> userDataService.updateUserAddress(
                    USER_ID, 48.8566, 2.3522, "New Street 2, Paris"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Hotel cannot change address");

            verify(userRepository).getOrThrow(USER_ID);
        }

        @Test
        @DisplayName("propagates exception when user does not exist")
        void updateUserAddress_shouldPropagate_whenUserNotFound() {
            // given
            when(userRepository.getOrThrow(USER_ID))
                    .thenThrow(new RuntimeException("User not found"));

            // when & then
            assertThatThrownBy(() -> userDataService.updateUserAddress(
                    USER_ID, VALID_LATITUDE, VALID_LONGITUDE, VALID_ADDRESS))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("User not found");

            verify(userRepository).getOrThrow(USER_ID);
        }
    }

    // ---------------------------------------------------------------------
    // updateUserNickname
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("updateUserNickname")
    class UpdateUserNickname {

        @Test
        @DisplayName("changes nickname when new nickname is valid and unique")
        void updateUserNickname_shouldChange_whenValidAndDifferent() {
            // given
            User user = new User(UserRole.ARTIST, VALID_NICKNAME, VALID_ADDRESS, VALID_LATITUDE, VALID_LONGITUDE);
            String newNickname = "picasso";
            when(userRepository.getOrThrow(USER_ID)).thenReturn(user);
            when(userRepository.existsByNickname(newNickname)).thenReturn(false);

            // when
            userDataService.updateUserNickname(USER_ID, newNickname);

            // then
            assertThat(user.getNickname()).isEqualTo(newNickname);
            verify(userRepository).getOrThrow(USER_ID);
            verify(userRepository).existsByNickname(newNickname);
        }

        @Test
        @DisplayName("skips update when nickname is identical")
        void updateUserNickname_shouldSkip_whenNicknameIdentical() {
            // given
            User user = new User(UserRole.ARTIST, VALID_NICKNAME, VALID_ADDRESS, VALID_LATITUDE, VALID_LONGITUDE);
            when(userRepository.getOrThrow(USER_ID)).thenReturn(user);

            // when
            userDataService.updateUserNickname(USER_ID, VALID_NICKNAME);

            // then
            assertThat(user.getNickname()).isEqualTo(VALID_NICKNAME);
            verify(userRepository).getOrThrow(USER_ID);
            verify(userRepository, never()).existsByNickname(anyString());
        }

        @Test
        @DisplayName("throws ConflictException when new nickname is already taken")
        void updateUserNickname_shouldThrow_whenNicknameTaken() {
            // given
            User user = new User(UserRole.ARTIST, VALID_NICKNAME, VALID_ADDRESS, VALID_LATITUDE, VALID_LONGITUDE);
            String newNickname = "picasso";
            when(userRepository.getOrThrow(USER_ID)).thenReturn(user);
            when(userRepository.existsByNickname(newNickname)).thenReturn(true);

            // when & then
            assertThatThrownBy(() -> userDataService.updateUserNickname(USER_ID, newNickname))
                    .isInstanceOf(ConflictException.class);

            assertThat(user.getNickname()).isEqualTo(VALID_NICKNAME);
            verify(userRepository).getOrThrow(USER_ID);
            verify(userRepository).existsByNickname(newNickname);
        }

        @Test
        @DisplayName("propagates exception when user does not exist")
        void updateUserNickname_shouldPropagate_whenUserNotFound() {
            // given
            when(userRepository.getOrThrow(USER_ID))
                    .thenThrow(new RuntimeException("User not found"));

            // when & then
            assertThatThrownBy(() -> userDataService.updateUserNickname(USER_ID, "picasso"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("User not found");

            verify(userRepository).getOrThrow(USER_ID);
        }
    }
}