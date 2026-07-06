package org.example.mypaintings.exception.painting;



public class IllegalStateTransitionException extends RuntimeException {
  public IllegalStateTransitionException(Object entity, Object currentState, Object targetState) {
    super(String.format("Cannot change '%s' status from '%s' to '%s'.", entity, currentState, targetState));
  }



}