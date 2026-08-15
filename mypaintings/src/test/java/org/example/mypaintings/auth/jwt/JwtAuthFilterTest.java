package org.example.mypaintings.auth.jwt;

import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@DisplayName("JwtAuthFilter Test Suite")
class JwtAuthFilterTest {

    private static final String TOKEN = "some.jwt.token";

    private JwtService jwtService;
    private JwtAuthFilter filter;

    @BeforeEach
    void setUp() {
        jwtService = mock(JwtService.class);
        filter = new JwtAuthFilter(jwtService, new ObjectMapper());
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Nested
    @DisplayName("doFilterInternal")
    class DoFilterInternal {

        @Test
        @DisplayName("authenticates user successfully when token is valid and contains required claims")
        void shouldAuthenticateWithUserIdAndRoleFromJwt() throws Exception {
            // given
            DecodedJWT jwt = decodedJwt(42L, "ROLE_USER");
            when(jwtService.verifyToken(TOKEN)).thenReturn(jwt);

            MockHttpServletRequest request = bearerRequest(TOKEN);
            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();

            // when
            filter.doFilter(request, response, chain);

            // then
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            assertThat(auth).isNotNull();
            assertThat(auth.getPrincipal()).isEqualTo(42L);
            assertThat(auth.getAuthorities())
                    .extracting(GrantedAuthority::getAuthority)
                    .containsExactly("ROLE_USER");

            assertThat(chain.getRequest()).isNotNull();
            assertThat(response.getStatus()).isEqualTo(200);
        }

        @Test
        @DisplayName("returns 401 when role claim is missing in JWT")
        void shouldReturn401WhenRoleClaimMissing() throws Exception {
            // given
            DecodedJWT jwt = decodedJwt(42L, null);
            when(jwtService.verifyToken(TOKEN)).thenReturn(jwt);

            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();

            // when
            filter.doFilter(bearerRequest(TOKEN), response, chain);

            // then
            assertThat(response.getStatus()).isEqualTo(401);
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
            assertThat(chain.getRequest()).isNull();
        }

        @Test
        @DisplayName("returns 401 when userId claim is missing in JWT")
        void shouldReturn401WhenUserIdClaimMissing() throws Exception {
            // given
            DecodedJWT jwt = decodedJwt(null, "ROLE_USER");
            when(jwtService.verifyToken(TOKEN)).thenReturn(jwt);

            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();

            // when
            filter.doFilter(bearerRequest(TOKEN), response, chain);

            // then
            assertThat(response.getStatus()).isEqualTo(401);
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
            assertThat(chain.getRequest()).isNull();
        }

        @Test
        @DisplayName("bypasses filter chain and proceeds when Authorization header is missing or invalid")
        void shouldBypassFilterWhenAuthorizationHeaderInvalid() throws Exception {
            // given
            MockHttpServletRequest request = new MockHttpServletRequest(); // brak nagłówka
            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();

            // when
            filter.doFilter(request, response, chain);

            // then
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
            assertThat(chain.getRequest()).isNotNull(); // przepuszczono dalej
            verifyNoInteractions(jwtService);
        }

        @Test
        @DisplayName("returns 401 when token verification fails (e.g. expired or invalid token)")
        void shouldReturn401WhenTokenVerificationFails() throws Exception {
            // given
            when(jwtService.verifyToken(TOKEN)).thenThrow(new JWTVerificationException("Invalid token"));

            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();

            // when
            filter.doFilter(bearerRequest(TOKEN), response, chain);

            // then
            assertThat(response.getStatus()).isEqualTo(401);
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
            assertThat(chain.getRequest()).isNull();
        }
    }

    // ---------- helpers ----------

    private static MockHttpServletRequest bearerRequest(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        return request;
    }

    private static DecodedJWT decodedJwt(Long userId, String role) {
        DecodedJWT jwt = mock(DecodedJWT.class);
        Claim userClaim = mock(Claim.class);
        Claim roleClaim = mock(Claim.class);

        when(jwt.getClaim("userId")).thenReturn(userClaim);
        when(userClaim.asLong()).thenReturn(userId);

        when(jwt.getClaim("role")).thenReturn(roleClaim);
        when(roleClaim.asString()).thenReturn(role);

        return jwt;
    }
}