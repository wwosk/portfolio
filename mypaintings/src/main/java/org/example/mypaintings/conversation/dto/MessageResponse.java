package org.example.mypaintings.conversation.dto;
import lombok.Builder;

import java.time.Instant;

@Builder
public record MessageResponse(
        long senderId,
        long receiverId,
        String message,
        Instant sentDate
) {}
