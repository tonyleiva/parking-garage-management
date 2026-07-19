package com.tonyleiva.parkinggarage.application.webhook;

import com.tonyleiva.parkinggarage.domain.webhook.ProcessedWebhookEvent;
import com.tonyleiva.parkinggarage.domain.webhook.WebhookEventType;
import com.tonyleiva.parkinggarage.domain.webhook.WebhookProcessingResult;
import com.tonyleiva.parkinggarage.infrastructure.persistence.ProcessedWebhookEventRepository;
import java.util.Optional;
import java.util.Arrays;
import org.springframework.stereotype.Component;

@Component
public class WebhookEventRecorder {
  private final ProcessedWebhookEventRepository repository;

  public WebhookEventRecorder(ProcessedWebhookEventRepository repository) {
    this.repository = repository;
  }

  public WebhookIdempotencyCheck check(
      IdempotencyKey key, WebhookEventType type, byte[] requestFingerprint) {
    Optional<ProcessedWebhookEvent> existing = repository.findByIdempotencyHash(key.hash());
    if (existing.isEmpty()) return WebhookIdempotencyCheck.notFound();
    ProcessedWebhookEvent event = existing.get();
    if (event.getIdempotencyKey().equals(key.logicalKey())
        && event.getEventType() == type
        && Arrays.equals(event.getRequestFingerprint(), requestFingerprint)) {
      return WebhookIdempotencyCheck.duplicate(toOutcome(event));
    }
    return WebhookIdempotencyCheck.conflict(
        new WebhookOutcome(
            WebhookProcessingResult.REJECTED,
            "A chave de idempotência já foi utilizada para outra requisição.",
            409,
            false));
  }

  public WebhookOutcome record(
      IdempotencyKey key,
      byte[] requestFingerprint,
      WebhookEventType type,
      String plate,
      WebhookProcessingResult result,
      int httpStatus,
      String message) {
    repository.saveAndFlush(
        new ProcessedWebhookEvent(
            key.logicalKey(), key.hash(), requestFingerprint, type, plate, result, httpStatus,
            message));
    return new WebhookOutcome(result, message, httpStatus, false);
  }

  private WebhookOutcome toOutcome(ProcessedWebhookEvent event) {
    return new WebhookOutcome(
        event.getResult(), event.getOperationalMessage(), event.getHttpStatus(), true);
  }
}
