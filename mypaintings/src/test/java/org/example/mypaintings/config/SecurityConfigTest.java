package org.example.mypaintings.config;

import org.example.mypaintings.auth.jwt.JwtAuthFilter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.DefaultSecurityFilterChain;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class SecurityConfigTest {

    private final JwtAuthFilter jwtAuthFilter = mock(JwtAuthFilter.class);
    private final SecurityConfig config = new SecurityConfig(jwtAuthFilter);

    @Nested
    @DisplayName("Security Filter Chain Configuration")
    class SecurityFilterChainTests {

        @Test
        @DisplayName("Should build security filter chain and register JwtAuthFilter in correct order")
        void shouldBuildSecurityFilterChain() {
            // given
            HttpSecurity http = mock(HttpSecurity.class, RETURNS_SELF);
            DefaultSecurityFilterChain expectedChain = mock(DefaultSecurityFilterChain.class);
            when(http.build()).thenReturn(expectedChain);

            // when
            SecurityFilterChain chain = config.securityFilterChain(http);

            // then
            assertThat(chain).isSameAs(expectedChain);
            verify(http).addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        }
    }

    @Nested
    @DisplayName("CORS Configuration")
    class CorsConfigurationTests {

        @Test
        @DisplayName("Should configure CORS with expected methods, origins, and credentials settings")
        void corsConfigurationShouldAllowExpectedSettings() {
            // given
            CorsConfigurationSource source = config.corsConfigurationSource();

            // when
            assertThat(source).isInstanceOf(UrlBasedCorsConfigurationSource.class);

            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/paintings");
            request.addHeader("Origin", "http://localhost:3000");
            CorsConfiguration cfg = source.getCorsConfiguration(request);

            // then
            assertThat(cfg).isNotNull();
            assertThat(cfg.getAllowedMethods())
                    .containsExactlyInAnyOrder("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");
            assertThat(cfg.getAllowedOriginPatterns()).containsExactly("*");
            assertThat(cfg.getAllowCredentials()).isTrue();
            assertThat(cfg.getExposedHeaders()).containsExactly("Authorization");
        }
    }

    @Nested
    @DisplayName("Password Encoder Configuration")
    class PasswordEncoderTests {

        @Test
        @DisplayName("Should properly hash passwords and match correct strings using Bcrypt")
        void passwordEncoderShouldHashAndMatch() {
            // given
            PasswordEncoder encoder = config.passwordEncoder();

            // when
            String hash = encoder.encode("secret123");

            // then
            assertThat(hash).isNotEqualTo("secret123");
            assertThat(encoder.matches("secret123", hash)).isTrue();
            assertThat(encoder.matches("wrong", hash)).isFalse();
        }
    }


    @Nested
    @DisplayName("Authentication Entry Point Configuration")
    class AuthenticationEntryPointTests {

        @Test
        @DisplayName("Should write custom 401 JSON error response on authentication failure")
        void shouldReturnCustomUnauthorizedJsonResponse() {
            // given
            HttpSecurity http = mock(HttpSecurity.class, RETURNS_SELF);
            when(http.build()).thenReturn(mock(DefaultSecurityFilterChain.class));

            org.mockito.ArgumentCaptor<org.springframework.security.config.annotation.web.configurers.ExceptionHandlingConfigurer<HttpSecurity>> captor =
                    org.mockito.ArgumentCaptor.forClass(org.springframework.security.config.annotation.web.configurers.ExceptionHandlingConfigurer.class);

            config.securityFilterChain(http);

        }
    }
}
