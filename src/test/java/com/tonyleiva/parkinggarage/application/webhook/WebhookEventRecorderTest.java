package com.tonyleiva.parkinggarage.application.webhook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.tonyleiva.parkinggarage.domain.webhook.ProcessedWebhookEvent;
import com.tonyleiva.parkinggarage.domain.webhook.WebhookEventType;
import com.tonyleiva.parkinggarage.domain.webhook.WebhookProcessingResult;
import com.tonyleiva.parkinggarage.infrastructure.persistence.ProcessedWebhookEventRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class WebhookEventRecorderTest {
  private final ProcessedWebhookEventRepository repository =
      mock(ProcessedWebhookEventRepository.class);
  private final WebhookEventRecorder recorder = new WebhookEventRecorder(repository);
  private final IdempotencyKey key = new IdempotencyKey("request-1", new byte[32]);
  private final byte[] fingerprint = filled((byte) 1);

  @Test
  void shouldReportNotFound() {
    when(repository.findByIdempotencyHash(key.hash())).thenReturn(Optional.empty());
    assertThat(recorder.check(key, WebhookEventType.ENTRY, fingerprint).status())
        .isEqualTo(WebhookIdempotencyStatus.NOT_FOUND);
  }

  @Test
  void shouldReportCompatibleDuplicate() {
    when(repository.findByIdempotencyHash(key.hash())).thenReturn(Optional.of(event(
        WebhookEventType.ENTRY, fingerprint)));
    var check = recorder.check(key, WebhookEventType.ENTRY, fingerprint);
    assertThat(check.status()).isEqualTo(WebhookIdempotencyStatus.DUPLICATE);
    assertThat(check.outcome().duplicate()).isTrue();
  }

  @Test
  void shouldReportConflictForDifferentFingerprintOrEventType() {
    when(repository.findByIdempotencyHash(key.hash())).thenReturn(Optional.of(event(
        WebhookEventType.ENTRY, fingerprint)));
    assertThat(recorder.check(key, WebhookEventType.ENTRY, filled((byte) 2)).status())
        .isEqualTo(WebhookIdempotencyStatus.CONFLICT);
    var differentType = recorder.check(key, WebhookEventType.EXIT, fingerprint);
    assertThat(differentType.status()).isEqualTo(WebhookIdempotencyStatus.CONFLICT);
    assertThat(differentType.outcome().httpStatus()).isEqualTo(409);
  }

  private ProcessedWebhookEvent event(WebhookEventType type, byte[] requestFingerprint) {
    return new ProcessedWebhookEvent(
        key.logicalKey(), key.hash(), requestFingerprint, type, "ZUL0001",
        WebhookProcessingResult.PROCESSED, 200, "Evento processado.");
  }

  private byte[] filled(byte value) {
    byte[] bytes = new byte[32];
    java.util.Arrays.fill(bytes, value);
    return bytes;
  }
}
