package org.example.mypaintings.feed.social.dto;


public record FollowedArtistResponse(
        Long id,
        String nickname,
        String initials
) {}