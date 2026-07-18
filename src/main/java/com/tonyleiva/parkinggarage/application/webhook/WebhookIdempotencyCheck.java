package com.tonyleiva.parkinggarage.application.webhook;

public record WebhookIdempotencyCheck(
    WebhookIdempotencyStatus status, WebhookOutcome outcome) {
  public static WebhookIdempotencyCheck notFound() {
    return new WebhookIdempotencyCheck(WebhookIdempotencyStatus.NOT_FOUND, null);
  }

  public static WebhookIdempotencyCheck duplicate(WebhookOutcome outcome) {
    return new WebhookIdempotencyCheck(WebhookIdempotencyStatus.DUPLICATE, outcome);
  }

  public static WebhookIdempotencyCheck conflict(WebhookOutcome outcome) {
    return new WebhookIdempotencyCheck(WebhookIdempotencyStatus.CONFLICT, outcome);
  }

  public boolean found() { return status != WebhookIdempotencyStatus.NOT_FOUND; }
}
