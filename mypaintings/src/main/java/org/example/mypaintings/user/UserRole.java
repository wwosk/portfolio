package org.example.mypaintings.user;

import java.util.Set;

public enum UserRole {
    HOTEL, ARTIST;

    private static final Set<UserRole> SELF_REGISTRATION_ALLOWED_ROLES =
            Set.of(UserRole.HOTEL, UserRole.ARTIST);

    public boolean isAllowedForSelfRegistration() {
        return SELF_REGISTRATION_ALLOWED_ROLES.contains(this);
    }
}
