package org.example.mypaintings.dashboard.artist.dto;

public record PostDashboardResponse(
        Long paintingId,
        String paintingTitle,
        String paintingUrl,
        long likesCount,
        boolean canBeDeleted
) {}