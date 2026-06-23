package org.example.mypaintings.exception.access;


import org.example.mypaintings.user.UserRole;

public class MissingRequiredRoleException extends RuntimeException {
    public MissingRequiredRoleException(UserRole role) {
        super(String.format("You don't have a needed role: %s", role.toString()));
    }

    public MissingRequiredRoleException(String message) {
        super(message);
    }
}