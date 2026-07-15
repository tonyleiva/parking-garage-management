package com.tonyleiva.parkinggarage.application.garage;

public class InvalidGarageConfigurationException extends RuntimeException {
  public InvalidGarageConfigurationException(String message) {
    super(message);
  }

  public InvalidGarageConfigurationException(String message, Throwable cause) {
    super(message, cause);
  }
}
