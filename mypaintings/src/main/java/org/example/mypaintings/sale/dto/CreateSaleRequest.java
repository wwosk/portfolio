package org.example.mypaintings.sale.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;



public record CreateSaleRequest(
        @Positive(message = "Painting ID must be positive.")
        long paintingId,
        boolean deliveryNeeded,

        @NotBlank(message = "Full address cannot be blank.")
        @Size(min = 5, message = "Address must be at least 5 characters long.")
        String fullAddress,

        @NotBlank(message = "Email is required.")
        String email,
        @NotBlank(message = "Phone number is required.")
        String phoneNumber
) {}
