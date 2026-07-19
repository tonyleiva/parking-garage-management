package com.tonyleiva.parkinggarage.application.webhook;

public class InvalidWebhookPayloadException extends RuntimeException {
  public InvalidWebhookPayloadException(String message) { super(message); }
}
