package com.tonyleiva.parkinggarage.application.revenue;

public class InvalidRevenueRequestException extends RuntimeException {
  public InvalidRevenueRequestException(String message) {
    super(message);
  }
}
