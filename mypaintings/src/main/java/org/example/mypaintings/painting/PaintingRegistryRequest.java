package org.example.mypaintings.painting;


import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.example.mypaintings.painting.features.PaintingMedium;
import org.example.mypaintings.painting.features.Unit;
import org.example.mypaintings.sale.financial.Money;

import java.util.List;

public record PaintingRegistryRequest(
        @NotBlank(message = "Painting title cannot be blank.")
        @Size(max = 100, message = "Painting title can be up to 100 characters long.")
        String title,

        @Size(max = 1000, message = "Description can be up to 1000 characters long.")
        String description,

        @NotNull(message = "Styles list cannot be null.")
        @NotEmpty(message = "Attachments list cannot be empty.")
        @Size(min =  1, max = 3, message = "Number of attachments must be between 1 and 3.")
        List<Long> attachedImageIds,

        @Positive(message = "Height must be greater than zero.")
        double height,

        @Positive(message = "Width must be greater than zero.")
        double width,

        @NotNull(message = "Unit of measurement is required.")
        Unit unit,

        @NotNull(message = "Price is required.")
        @Valid
        Money price,

        @Size(min = 1, max = 3, message = "Number of styles must be between 1 and 3.")
        @NotNull(message = "Styles list cannot be null.")
        List<@NotNull @Positive(message = "Style ID must be a positive number.") Long> styleIds,

        @NotNull(message = "Medium is required.")
        PaintingMedium medium
) {}
