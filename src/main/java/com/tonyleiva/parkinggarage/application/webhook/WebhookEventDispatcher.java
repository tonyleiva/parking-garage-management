package com.tonyleiva.parkinggarage.application.webhook;

import com.tonyleiva.parkinggarage.domain.webhook.WebhookEventType;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class WebhookEventDispatcher {
  private final Map<WebhookEventType, WebhookEventHandler> handlers;

  public WebhookEventDispatcher(List<WebhookEventHandler> handlers) {
    Map<WebhookEventType, WebhookEventHandler> mapped = new EnumMap<>(WebhookEventType.class);
    for (WebhookEventHandler handler : handlers) {
      if (mapped.putIfAbsent(handler.supportedType(), handler) != null) {
        throw new IllegalStateException(
            "Existe mais de um handler configurado para o evento " + handler.supportedType() + ".");
      }
    }
    this.handlers = Map.copyOf(mapped);
  }

  public WebhookOutcome dispatch(WebhookRequest request, String idempotencyKey) {
    if (request.eventType() == null) {
      throw new InvalidWebhookPayloadException("O tipo do evento é obrigatório.");
    }
    WebhookEventHandler handler = handlers.get(request.eventType());
    if (handler == null) {
      throw new InvalidWebhookPayloadException("O tipo do evento não é suportado.");
    }
    return handler.process(request, idempotencyKey);
  }
}
