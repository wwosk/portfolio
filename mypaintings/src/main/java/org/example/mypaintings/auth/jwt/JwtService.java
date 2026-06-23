package org.example.mypaintings.auth.jwt;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Date;


@Service
public class JwtService {

    @Value("${app.jwt.secret}")
    private String secretKey;

    @Value("${app.jwt.expiration-ms}")
    private long expirationMs;

    public String generateToken(Long userId, String role) {
        return JWT.create()
                .withClaim("userId", userId)
                .withClaim("role", "ROLE_" + role)
                .withIssuer("MyPaintings")
                .withAudience("mypaintings-api")
                .withIssuedAt(new Date())
                .withExpiresAt(new Date(System.currentTimeMillis() + expirationMs))
                .sign(Algorithm.HMAC256(secretKey));
    }

    public DecodedJWT verifyToken(String token) {
        return JWT.require(Algorithm.HMAC256(secretKey))
                .withIssuer("MyPaintings")
                .withAudience("mypaintings-api")
                .build()
                .verify(token);
    }

    @PostConstruct
    void validate() {
        if (secretKey == null || secretKey.length() < 32) {
            throw new IllegalStateException("app.jwt.secret must be at least 32 chars");
        }
    }


}