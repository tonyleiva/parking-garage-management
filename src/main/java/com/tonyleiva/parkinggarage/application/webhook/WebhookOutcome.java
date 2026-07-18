package com.tonyleiva.parkinggarage.application.webhook;

import com.tonyleiva.parkinggarage.domain.webhook.WebhookProcessingResult;

public record WebhookOutcome(
    WebhookProcessingResult status, String message, int httpStatus, boolean duplicate) {}
