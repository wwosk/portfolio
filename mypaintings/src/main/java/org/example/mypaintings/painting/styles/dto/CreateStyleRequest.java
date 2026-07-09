package org.example.mypaintings.painting.styles.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Builder;


@Builder
public record CreateStyleRequest(
        @NotBlank(message = "Style name cannot be blank")
        @Size(max = 50, message = "Style name can be up to 50 characters long")
        String name
) {}