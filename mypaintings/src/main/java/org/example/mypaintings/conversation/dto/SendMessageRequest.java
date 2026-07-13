package org.example.mypaintings.conversation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Builder;

@Builder
public record SendMessageRequest(
        @NotBlank(message = "Message content cannot be blank.")
        @Size(min = 1, max = 2048, message = "Message length must be between 1 and 2048 characters.")
        String message
) {}