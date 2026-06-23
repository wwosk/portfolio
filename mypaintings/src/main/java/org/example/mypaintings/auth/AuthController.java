package org.example.mypaintings.auth;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.mypaintings.auth.dto.AuthResponse;
import org.example.mypaintings.auth.dto.LoginRequest;
import org.example.mypaintings.auth.dto.RegisterRequest;
import org.example.mypaintings.security.LoginRateLimited;
import org.example.mypaintings.security.RateLimited;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;


    @RateLimited
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@RequestBody @Valid RegisterRequest request) {
        AuthResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @LoginRateLimited
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody @Valid LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }
}