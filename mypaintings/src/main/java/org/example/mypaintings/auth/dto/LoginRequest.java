package org.example.mypaintings.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "Email address cannot be blank.")
        @Email(message = "Invalid email address format.")
        String email,

        @NotBlank(message = "Password cannot be blank.")
        String password
) {}
