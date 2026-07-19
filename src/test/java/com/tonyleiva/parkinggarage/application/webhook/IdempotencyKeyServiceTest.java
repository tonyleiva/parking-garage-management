package com.tonyleiva.parkinggarage.application.webhook;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class IdempotencyKeyServiceTest {
  private final IdempotencyKeyService service = new IdempotencyKeyService();

  @Test
  void shouldNormalizePlateAndParkedCoordinates() {
    String plate = service.normalizePlate(" zul0001 ");
    var key = service.parked(plate, Instant.parse("2025-01-01T12:00:00Z"),
        new BigDecimal("-23.56168400"), new BigDecimal("-46.655981000"));
    assertThat(key.logicalKey()).isEqualTo(
        "PARKED|ZUL0001|2025-01-01T12:00:00Z|-23.561684|-46.655981");
    assertThat(key.hash()).hasSize(32);
  }

  @Test
  void shouldUseExplicitMarkerWithoutActiveSession() {
    var key = service.parkedWithoutActiveSession(
        "ZUL0001", new BigDecimal("-23.56168400"), new BigDecimal("-46.655981000"));
    assertThat(key.logicalKey()).isEqualTo(
        "PARKED|ZUL0001|NO_ACTIVE_SESSION|-23.561684|-46.655981");
  }

  @Test
  void shouldTrimProducerKey() {
    assertThat(service.fromHeader(" request-123 ").logicalKey()).isEqualTo("request-123");
  }

  @Test
  void shouldFingerprintOnlyRelevantCanonicalFields() {
    var request = new WebhookRequest(
        "ignored", null, null, new BigDecimal("-23.56168400"),
        new BigDecimal("-46.655981000"),
        com.tonyleiva.parkinggarage.domain.webhook.WebhookEventType.PARKED);
    assertThat(service.fingerprint(request, "ZUL0001"))
        .isEqualTo(service.fingerprint(
            new WebhookRequest("other", null, null, new BigDecimal("-23.561684"),
                new BigDecimal("-46.655981"),
                com.tonyleiva.parkinggarage.domain.webhook.WebhookEventType.PARKED),
            "ZUL0001"));
  }
}
