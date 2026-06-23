package org.example.mypaintings.auth.dto;

import jakarta.validation.constraints.*;
import org.example.mypaintings.user.UserRole;
import org.example.mypaintings.validation.StrongPassword;

public record RegisterRequest(
        @NotBlank(message = "Email address cannot be blank.")
        @Email(message = "Invalid email address format.")
        String email,

        @NotBlank(message = "Password cannot be blank.")
        @StrongPassword
        String password,

        @NotBlank(message = "Nickname cannot be blank.")
        @Size(min = 2, max = 50, message = "Nickname must be between 2 and 50 characters long.")
        String nickname,

        @NotNull(message = "User role is required.")
        UserRole role,

        @NotBlank(message = "Full address cannot be blank.")
        @Size(min = 10, message = "Address must be at least 10 characters long.")
        String fullAddress,

        @DecimalMin(value = "-90.0", message = "Latitude cannot be less than -90.")
        @DecimalMax(value = "90.0", message = "Latitude cannot be greater than 90.")
        @NotNull(message = "Latitude cannot be null.")
        Double latitude,

        @DecimalMin(value = "-180.0", message = "Longitude cannot be less than -180.")
        @DecimalMax(value = "180.0", message = "Longitude cannot be greater than 180.")
        @NotNull(message = "Longitude cannot be null.")
        Double longitude
) {}
