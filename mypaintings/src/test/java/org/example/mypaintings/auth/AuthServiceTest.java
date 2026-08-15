package org.example.mypaintings.auth;

import org.example.mypaintings.auth.dto.AuthResponse;
import org.example.mypaintings.auth.dto.LoginRequest;
import org.example.mypaintings.auth.dto.RegisterRequest;
import org.example.mypaintings.auth.jwt.JwtService;
import org.example.mypaintings.exception.validation.BusinessValidationException;
import org.example.mypaintings.user.User;
import org.example.mypaintings.user.UserDataService;
import org.example.mypaintings.user.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthServiceTest {

    @Mock UserCredentialRepository userCredentialRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock JwtService jwtService;
    @Mock UserDataService userDataService;

    @InjectMocks AuthService authService;

    private static final Long USER_ID = 99L;
    private static final String EMAIL = "artist@example.com";
    private static final String RAW_PASSWORD = "Secret123!";
    private static final String ENCODED_PASSWORD = "$2a$10$encodedhash";
    private static final String NICKNAME = "monet";
    private static final String ADDRESS = "Museum Street 1, Amsterdam";
    private static final double LAT = 52.3676;
    private static final double LON = 4.9041;
    private static final String TOKEN = "jwt.token.value";

    private User mockUser(UserRole role) {
        User user = mock(User.class);
        when(user.getId()).thenReturn(USER_ID);
        when(user.getRole()).thenReturn(role);
        return user;
    }

    private UserCredential mockCredential(User user, String encodedPassword) {
        UserCredential credential = mock(UserCredential.class);
        when(credential.getUser()).thenReturn(user);
        when(credential.getPassword()).thenReturn(encodedPassword);
        when(credential.getEmail()).thenReturn(EMAIL);
        return credential;
    }

    private RegisterRequest registerRequest(UserRole role) {
        return new RegisterRequest(EMAIL, RAW_PASSWORD, NICKNAME, role, ADDRESS, LAT, LON);
    }

    private LoginRequest loginRequest() {
        return new LoginRequest(EMAIL, RAW_PASSWORD);
    }

    // =====================================================================
    // register
    // =====================================================================

    @Nested
    @DisplayName("register")
    class Register {

        @Test
        @DisplayName("registers ARTIST, hashes password, saves and returns token")
        void register_shouldRegisterArtist_andReturnToken() {
            // given
            User user = mockUser(UserRole.ARTIST);
            when(userDataService.createUser(UserRole.ARTIST, NICKNAME, LAT, LON, ADDRESS))
                    .thenReturn(user);
            when(passwordEncoder.encode(RAW_PASSWORD)).thenReturn(ENCODED_PASSWORD);
            when(jwtService.generateToken(USER_ID, "ARTIST")).thenReturn(TOKEN);

            // when
            AuthResponse response = authService.register(registerRequest(UserRole.ARTIST));

            // then
            assertThat(response.token()).isEqualTo(TOKEN);

            ArgumentCaptor<UserCredential> captor = ArgumentCaptor.forClass(UserCredential.class);
            verify(userCredentialRepository).saveAndFlush(captor.capture());
            UserCredential saved = captor.getValue();
            assertThat(saved.getUser()).isEqualTo(user);
            assertThat(saved.getEmail()).isEqualTo(EMAIL);
            assertThat(saved.getPassword()).isEqualTo(ENCODED_PASSWORD);

            verify(passwordEncoder).encode(RAW_PASSWORD);
            verify(jwtService).generateToken(USER_ID, "ARTIST");
        }

        @Test
        @DisplayName("registers HOTEL when role is allowed")
        void register_shouldRegisterHotel_whenRoleAllowed() {
            // given
            User user = mockUser(UserRole.HOTEL);
            when(userDataService.createUser(UserRole.HOTEL, NICKNAME, LAT, LON, ADDRESS))
                    .thenReturn(user);
            when(passwordEncoder.encode(RAW_PASSWORD)).thenReturn(ENCODED_PASSWORD);
            when(jwtService.generateToken(USER_ID, "HOTEL")).thenReturn(TOKEN);

            // when
            AuthResponse response = authService.register(registerRequest(UserRole.HOTEL));

            // then
            assertThat(response.token()).isEqualTo(TOKEN);
            verify(userDataService).createUser(UserRole.HOTEL, NICKNAME, LAT, LON, ADDRESS);
        }

        @Test
        @DisplayName("throws BusinessValidationException when role is not allowed for self-registration")
        void register_shouldThrow_whenRoleNotAllowedForSelfRegistration() {
            for (UserRole role : UserRole.values()) {
                if (!role.isAllowedForSelfRegistration()) {
                    // when & then
                    assertThatThrownBy(() -> authService.register(registerRequest(role)))
                            .isInstanceOf(BusinessValidationException.class)
                            .hasMessageContaining("not allowed for self-registration");
                    verifyNoInteractions(userDataService, passwordEncoder, userCredentialRepository, jwtService);
                    return;
                }
            }
        }

        @Test
        @DisplayName("propagates exception when user creation fails")
        void register_shouldPropagate_whenUserCreationFails() {
            // given
            when(userDataService.createUser(any(), anyString(), anyDouble(), anyDouble(), anyString()))
                    .thenThrow(new BusinessValidationException("Nickname cannot be empty"));

            // when & then
            assertThatThrownBy(() -> authService.register(registerRequest(UserRole.ARTIST)))
                    .isInstanceOf(BusinessValidationException.class)
                    .hasMessage("Nickname cannot be empty");

            verifyNoInteractions(passwordEncoder, userCredentialRepository, jwtService);
        }

        @Test
        @DisplayName("propagates exception when database save fails (e.g. duplicate email)")
        void register_shouldPropagate_whenSaveFails() {
            // given
            User user = mockUser(UserRole.ARTIST);
            when(userDataService.createUser(any(), anyString(), anyDouble(), anyDouble(), anyString()))
                    .thenReturn(user);
            when(passwordEncoder.encode(RAW_PASSWORD)).thenReturn(ENCODED_PASSWORD);
            when(userCredentialRepository.saveAndFlush(any(UserCredential.class)))
                    .thenThrow(new org.springframework.dao.DataIntegrityViolationException("dup"));

            // when & then
            assertThatThrownBy(() -> authService.register(registerRequest(UserRole.ARTIST)))
                    .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);

            verifyNoInteractions(jwtService);
        }

        @Test
        @DisplayName("propagates exception when token generation fails")
        void register_shouldPropagate_whenJwtGenerationFails() {
            // given
            User user = mockUser(UserRole.ARTIST);
            when(userDataService.createUser(any(), anyString(), anyDouble(), anyDouble(), anyString()))
                    .thenReturn(user);
            when(passwordEncoder.encode(RAW_PASSWORD)).thenReturn(ENCODED_PASSWORD);
            when(jwtService.generateToken(anyLong(), anyString()))
                    .thenThrow(new IllegalStateException("JWT error"));

            // when & then
            assertThatThrownBy(() -> authService.register(registerRequest(UserRole.ARTIST)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("JWT error");

            verify(userCredentialRepository).saveAndFlush(any(UserCredential.class));
        }
    }

    // =====================================================================
    // login
    // =====================================================================

    @Nested
    @DisplayName("login")
    class Login {

        @Test
        @DisplayName("returns token when credentials are valid")
        void login_shouldReturnToken_whenCredentialsValid() {
            // given
            User user = mockUser(UserRole.ARTIST);
            UserCredential credential = mockCredential(user, ENCODED_PASSWORD);

            when(userCredentialRepository.findByEmailWithUser(EMAIL)).thenReturn(Optional.of(credential));
            when(passwordEncoder.matches(RAW_PASSWORD, ENCODED_PASSWORD)).thenReturn(true);
            when(jwtService.generateToken(USER_ID, "ARTIST")).thenReturn(TOKEN);

            // when
            AuthResponse response = authService.login(loginRequest());

            // then
            assertThat(response.token()).isEqualTo(TOKEN);
            verify(jwtService).generateToken(USER_ID, "ARTIST");
        }

        @Test
        @DisplayName("throws BadCredentialsException when email is not found")
        void login_shouldThrow_whenEmailNotFound() {
            // given
            when(userCredentialRepository.findByEmailWithUser(EMAIL)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> authService.login(loginRequest()))
                    .isInstanceOf(BadCredentialsException.class)
                    .hasMessage("Invalid email or password");

            verifyNoInteractions(passwordEncoder, jwtService);
        }

        @Test
        @DisplayName("throws BadCredentialsException when password does not match")
        void login_shouldThrow_whenPasswordDoesNotMatch() {
            // given
            User user = mockUser(UserRole.ARTIST);
            UserCredential credential = mockCredential(user, ENCODED_PASSWORD);

            when(userCredentialRepository.findByEmailWithUser(EMAIL)).thenReturn(Optional.of(credential));
            when(passwordEncoder.matches(RAW_PASSWORD, ENCODED_PASSWORD)).thenReturn(false);

            // when & then
            assertThatThrownBy(() -> authService.login(loginRequest()))
                    .isInstanceOf(BadCredentialsException.class)
                    .hasMessage("Invalid email or password");

            verifyNoInteractions(jwtService);
        }

        @Test
        @DisplayName("uses identical error message for unknown email and wrong password")
        void login_shouldUseSameMessage_forUnknownEmailAndWrongPassword() {
            // given (unknown email)
            when(userCredentialRepository.findByEmailWithUser(EMAIL)).thenReturn(Optional.empty());
            Throwable unknownEmail = org.assertj.core.api.Assertions.catchThrowable(
                    () -> authService.login(loginRequest()));

            // given (wrong password)
            User user = mockUser(UserRole.ARTIST);
            UserCredential credential = mockCredential(user, ENCODED_PASSWORD);
            when(userCredentialRepository.findByEmailWithUser(EMAIL)).thenReturn(Optional.of(credential));
            when(passwordEncoder.matches(RAW_PASSWORD, ENCODED_PASSWORD)).thenReturn(false);
            Throwable wrongPassword = org.assertj.core.api.Assertions.catchThrowable(
                    () -> authService.login(loginRequest()));

            // when & then
            assertThat(unknownEmail).hasMessage(wrongPassword.getMessage());
        }

        @Test
        @DisplayName("generates token with actual role of the logged in user (HOTEL)")
        void login_shouldGenerateTokenWithActualRole() {
            // given
            User user = mockUser(UserRole.HOTEL);
            UserCredential credential = mockCredential(user, ENCODED_PASSWORD);

            when(userCredentialRepository.findByEmailWithUser(EMAIL)).thenReturn(Optional.of(credential));
            when(passwordEncoder.matches(RAW_PASSWORD, ENCODED_PASSWORD)).thenReturn(true);
            when(jwtService.generateToken(USER_ID, "HOTEL")).thenReturn(TOKEN);

            // when
            authService.login(loginRequest());

            // then
            verify(jwtService).generateToken(USER_ID, "HOTEL");
        }
    }
}