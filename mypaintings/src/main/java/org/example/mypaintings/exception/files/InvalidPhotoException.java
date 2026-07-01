package org.example.mypaintings.exception.files;


public class InvalidPhotoException extends RuntimeException {
    public InvalidPhotoException(String fileName, String reason) {
        super(String.format("Invalid files '%s': %s", fileName, reason));
    }

}