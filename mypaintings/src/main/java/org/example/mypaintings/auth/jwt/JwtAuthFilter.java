package org.example.mypaintings.auth.jwt;

import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.mypaintings.exception.ErrorResponse;
import org.example.mypaintings.exception.access.UnauthorizedAccessException;
import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(BEARER_PREFIX.length());

        try {
            DecodedJWT decodedJWT = jwtService.verifyToken(token);
            String role = decodedJWT.getClaim("role").asString();
            Long userId = decodedJWT.getClaim("userId").asLong();

            var authToken = getAuthToken(role, userId);
            // KLUCZOWE: RateLimitingAspect czyta details jako Long, żeby kluczować
            // bucket po userId, a nie po IP. Bez tego wielu userów za NAT-em
            // dzieliłoby jeden bucket.
            authToken.setDetails(userId);

            SecurityContextHolder.getContext().setAuthentication(authToken);
            log.debug("Authenticated userId={}, role={}", userId, role);

        } catch (JWTVerificationException | UnauthorizedAccessException e) {
            log.warn("JWT authentication failed: {}", e.getMessage());
            SecurityContextHolder.clearContext();
            writeUnauthorized(response);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private static @NonNull UsernamePasswordAuthenticationToken getAuthToken(String role, Long userId) {
        if (role == null || userId == null) {
            throw new UnauthorizedAccessException("Missing required claims in JWT");
        }

        var authorities = List.of(new SimpleGrantedAuthority(role));

        return new UsernamePasswordAuthenticationToken(
                userId,
                null,
                authorities
        );
    }

    private void writeUnauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        ErrorResponse error = new ErrorResponse(
                HttpStatus.UNAUTHORIZED.value(),
                "Invalid or expired JWT token."
        );

        objectMapper.writeValue(response.getWriter(), error);
    }
}