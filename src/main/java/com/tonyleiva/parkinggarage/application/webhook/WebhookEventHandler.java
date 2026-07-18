package com.tonyleiva.parkinggarage.application.webhook;

import com.tonyleiva.parkinggarage.domain.webhook.WebhookEventType;

public interface WebhookEventHandler {
  WebhookEventType supportedType();

  WebhookOutcome process(WebhookRequest request, String idempotencyKey);
}
