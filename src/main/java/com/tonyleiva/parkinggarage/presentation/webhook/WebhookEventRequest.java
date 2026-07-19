package com.tonyleiva.parkinggarage.presentation.webhook;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.tonyleiva.parkinggarage.application.webhook.WebhookRequest;
import com.tonyleiva.parkinggarage.domain.webhook.WebhookEventType;
import java.math.BigDecimal;
import java.time.Instant;

public record WebhookEventRequest(
    @JsonProperty("license_plate") String licensePlate,
    @JsonProperty("entry_time") Instant entryTime,
    @JsonProperty("exit_time") Instant exitTime,
    @JsonProperty("lat") BigDecimal latitude,
    @JsonProperty("lng") BigDecimal longitude,
    @JsonProperty("event_type") WebhookEventType eventType) {
  public WebhookRequest toApplicationRequest() {
    return new WebhookRequest(licensePlate, entryTime, exitTime, latitude, longitude, eventType);
  }
}
