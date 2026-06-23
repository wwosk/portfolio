package org.example.mypaintings.exception.notfound;

public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(Class<?> resourceClass, Object identifier) {
        super(String.format("%s not found with identifier: '%s'",
                resourceClass.getSimpleName(), identifier));
    }

    public ResourceNotFoundException(Class<?> resourceClass, String reason) {
        super(String.format("%s not found with identifier: '%s'",
                resourceClass.getSimpleName(), reason));
    }

    public ResourceNotFoundException(String message) {
        super(message);
    }


}