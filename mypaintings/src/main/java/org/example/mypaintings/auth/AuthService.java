package org.example.mypaintings.auth;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.mypaintings.auth.dto.AuthResponse;
import org.example.mypaintings.auth.dto.LoginRequest;
import org.example.mypaintings.auth.dto.RegisterRequest;
import org.example.mypaintings.auth.jwt.JwtService;
import org.example.mypaintings.exception.validation.BusinessValidationException;
import org.example.mypaintings.user.User;
import org.example.mypaintings.user.UserDataService;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {


    private final UserCredentialRepository userCredentialRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final UserDataService userDataService;

    @Transactional
    public AuthResponse register(RegisterRequest request) {

        if (!request.role().isAllowedForSelfRegistration()) {
            throw new BusinessValidationException("Role " + request.role() + " is not allowed for self-registration.");
        }

        User user = userDataService.createUser(request.role(), request.nickname(), request.latitude(), request.longitude(), request.fullAddress());
        String encodedPassword = passwordEncoder.encode(request.password());
        UserCredential credential = new UserCredential(user, request.email(), encodedPassword);

        userCredentialRepository.saveAndFlush(credential);

        log.info("New user successfully registered with ID: {}", user.getId());

        String token = jwtService.generateToken(user.getId(), user.getRole().name());
        return new AuthResponse(token);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        UserCredential credential = userCredentialRepository.findByEmailWithUser(request.email())
                .orElseThrow(() -> {
                    log.warn("Login attempt failed: email not found");
                    return new BadCredentialsException("Invalid email or password");
                });

        if (!passwordEncoder.matches(request.password(), credential.getPassword())) {
            log.warn("Login attempt failed: invalid password");
            throw new BadCredentialsException("Invalid email or password");
        }

        User user = credential.getUser();
        log.info("User successfully logged in with id: {}",user.getId());
        String token = jwtService.generateToken(user.getId(), user.getRole().name());

        return new AuthResponse(token);
    }



}