package org.example.mypaintings.exception.duplicate;

public class ConflictException extends RuntimeException {

    public ConflictException(String fieldName, Object value) {
        super(String.format("Resource with field '%s' and value '%s' already exists.", fieldName, value));
    }
    public ConflictException(String message) {
        super(message);
    }

}