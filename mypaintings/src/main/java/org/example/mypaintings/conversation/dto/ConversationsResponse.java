package org.example.mypaintings.conversation.dto;

import lombok.Builder;

@Builder
public record ConversationsResponse(
        long id,
        long artistId,
        String paintingUrl,
        String paintingTitle,
        String otherUserNickname
) {}
