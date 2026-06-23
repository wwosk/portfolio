package org.example.mypaintings.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateNicknameRequest(
        @NotBlank(message = "Nickname cannot be blank.")
        @Size(max = 30, message = "Nickname must be between 3 and 30 characters long.")
        String nickname
) {}