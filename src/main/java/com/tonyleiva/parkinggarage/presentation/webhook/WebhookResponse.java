package com.tonyleiva.parkinggarage.presentation.webhook;

public record WebhookResponse(String status, String message, boolean duplicate) {}
