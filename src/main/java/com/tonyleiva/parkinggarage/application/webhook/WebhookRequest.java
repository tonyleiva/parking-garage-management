package com.tonyleiva.parkinggarage.application.webhook;

import com.tonyleiva.parkinggarage.domain.webhook.WebhookEventType;
import java.math.BigDecimal;
import java.time.Instant;

public record WebhookRequest(
    String licensePlate,
    Instant entryTime,
    Instant exitTime,
    BigDecimal latitude,
    BigDecimal longitude,
    WebhookEventType eventType) {}
