package com.tonyleiva.parkinggarage.domain.session;

public class InvalidSessionTransitionException extends RuntimeException {
  public InvalidSessionTransitionException(String message) {
    super(message);
  }
}
