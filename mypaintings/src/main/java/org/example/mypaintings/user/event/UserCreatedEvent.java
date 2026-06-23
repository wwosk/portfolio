package org.example.mypaintings.user.event;

import org.example.mypaintings.user.UserRole;

public record UserCreatedEvent(
        long userId,
        UserRole role,
        Double latitude,
        Double longitude
) {}