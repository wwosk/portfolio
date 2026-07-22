package org.example.mypaintings.feed.social.dto;



public record ArtistStatsResponse(
        String nickname,
        long followersCount,
        long paintingsCount
) {}