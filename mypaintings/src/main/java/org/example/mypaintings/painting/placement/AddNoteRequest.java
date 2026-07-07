package org.example.mypaintings.painting.placement;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Builder;



@Builder
public record AddNoteRequest(
        @NotBlank(message = "Note cannot be blank.")
        @Size(max = 300, message = "Note can be up to 300 characters long.")
        String note
) {}