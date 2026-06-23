package org.example.mypaintings.user.dto;

import jakarta.validation.constraints.*;

public record UpdateAddressRequest(
        @NotBlank(message = "Full address cannot be blank.")
        @Size(min = 5, message = "Address must be at least 5 characters long.")
        String fullAddress,

        @NotNull
        @DecimalMin(value = "-90.0", message = "Latitude cannot be less than -90.")
        @DecimalMax(value = "90.0", message = "Latitude cannot be greater than 90.")
        Double latitude,

        @NotNull
        @DecimalMin(value = "-180.0", message = "Longitude cannot be less than -180.")
        @DecimalMax(value = "180.0", message = "Longitude cannot be greater than 180.")
        Double longitude
) {}