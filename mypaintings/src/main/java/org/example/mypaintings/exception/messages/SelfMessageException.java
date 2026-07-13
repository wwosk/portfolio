package org.example.mypaintings.exception.messages;



public class SelfMessageException extends RuntimeException {
    public SelfMessageException(String message) {
        super(message);
    }
}