package com.tonyleiva.parkinggarage.application.webhook;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.tonyleiva.parkinggarage.domain.pricing.DynamicPricingPolicy;
import com.tonyleiva.parkinggarage.domain.pricing.ParkingFeePolicy;
import com.tonyleiva.parkinggarage.domain.webhook.WebhookEventType;
import com.tonyleiva.parkinggarage.infrastructure.persistence.GarageSectorRepository;
import com.tonyleiva.parkinggarage.infrastructure.persistence.ParkingSessionRepository;
import com.tonyleiva.parkinggarage.infrastructure.persistence.ParkingSpotRepository;
import java.time.Clock;
import org.junit.jupiter.api.Test;

class WebhookHandlersValidationTest {
  private final IdempotencyKeyService keys = new IdempotencyKeyService();
  private final WebhookEventRecorder recorder = mock(WebhookEventRecorder.class);
  private final ParkingSessionRepository sessions = mock(ParkingSessionRepository.class);
  private final ParkingSpotRepository spots = mock(ParkingSpotRepository.class);
  private final GarageSectorRepository sectors = mock(GarageSectorRepository.class);

  @Test
  void entryShouldRequireEntryTime() {
    var handler = new EntryEventHandler(keys, recorder, sessions, spots, sectors);
    assertThatThrownBy(() -> handler.process(
        new WebhookRequest("ZUL0001", null, null, null, null, WebhookEventType.ENTRY), null))
        .isInstanceOf(InvalidWebhookPayloadException.class)
        .hasMessage("O horário de entrada é obrigatório.");
  }

  @Test
  void parkedShouldRequireCoordinates() {
    var handler = new ParkedEventHandler(keys, recorder, sessions, spots, sectors,
        new DynamicPricingPolicy(), Clock.systemUTC());
    assertThatThrownBy(() -> handler.process(
        new WebhookRequest("ZUL0001", null, null, null, null, WebhookEventType.PARKED), null))
        .isInstanceOf(InvalidWebhookPayloadException.class)
        .hasMessage("As coordenadas da vaga são obrigatórias.");
  }

  @Test
  void exitShouldRequireExitTime() {
    var handler = new ExitEventHandler(keys, recorder, sessions, spots, new ParkingFeePolicy());
    assertThatThrownBy(() -> handler.process(
        new WebhookRequest("ZUL0001", null, null, null, null, WebhookEventType.EXIT), null))
        .isInstanceOf(InvalidWebhookPayloadException.class)
        .hasMessage("O horário de saída é obrigatório.");
  }
}
