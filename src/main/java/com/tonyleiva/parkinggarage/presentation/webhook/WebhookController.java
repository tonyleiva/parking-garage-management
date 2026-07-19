package com.tonyleiva.parkinggarage.presentation.webhook;

import com.tonyleiva.parkinggarage.application.webhook.WebhookOutcome;
import com.tonyleiva.parkinggarage.application.webhook.WebhookRequestProcessor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class WebhookController {
  private final WebhookRequestProcessor processor;

  public WebhookController(WebhookRequestProcessor processor) { this.processor = processor; }

  @PostMapping("/webhook")
  public ResponseEntity<WebhookResponse> receive(
      @RequestBody WebhookEventRequest request,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
    WebhookOutcome outcome = processor.process(request.toApplicationRequest(), idempotencyKey);
    return ResponseEntity.status(outcome.httpStatus())
        .body(new WebhookResponse(
            outcome.status().name(), outcome.message(), outcome.duplicate()));
  }
}
