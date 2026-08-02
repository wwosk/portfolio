package org.example.mypaintings.feed.dto;

import java.util.List;


public record PaintingFeedItemResponse(
        Long id,
        String title,
        List<String> imageUrls,
        boolean isLiked,
        String artistNickname,
        Long artistId
) {}