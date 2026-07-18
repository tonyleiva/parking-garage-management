package com.tonyleiva.parkinggarage.application.webhook;

public enum WebhookIdempotencyStatus {
  NOT_FOUND,
  DUPLICATE,
  CONFLICT
}
