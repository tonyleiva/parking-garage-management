package com.tonyleiva.parkinggarage.application.webhook;

import com.tonyleiva.parkinggarage.domain.pricing.DynamicPrice;
import com.tonyleiva.parkinggarage.domain.pricing.DynamicPricingPolicy;
import com.tonyleiva.parkinggarage.domain.session.ParkingSession;
import com.tonyleiva.parkinggarage.domain.session.ParkingSessionStatus;
import com.tonyleiva.parkinggarage.domain.webhook.WebhookEventType;
import com.tonyleiva.parkinggarage.domain.webhook.WebhookProcessingResult;
import com.tonyleiva.parkinggarage.infrastructure.persistence.GarageSectorRepository;
import com.tonyleiva.parkinggarage.infrastructure.persistence.ParkingSessionRepository;
import com.tonyleiva.parkinggarage.infrastructure.persistence.ParkingSpotRepository;
import java.time.Clock;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

@Component
public class ParkedEventHandler implements WebhookEventHandler {
  private final IdempotencyKeyService keyService;
  private final WebhookEventRecorder recorder;
  private final ParkingSessionRepository sessionRepository;
  private final ParkingSpotRepository spotRepository;
  private final GarageSectorRepository sectorRepository;
  private final DynamicPricingPolicy pricingPolicy;
  private final Clock clock;

  public ParkedEventHandler(
      IdempotencyKeyService keyService,
      WebhookEventRecorder recorder,
      ParkingSessionRepository sessionRepository,
      ParkingSpotRepository spotRepository,
      GarageSectorRepository sectorRepository,
      DynamicPricingPolicy pricingPolicy,
      Clock clock) {
    this.keyService = keyService;
    this.recorder = recorder;
    this.sessionRepository = sessionRepository;
    this.spotRepository = spotRepository;
    this.sectorRepository = sectorRepository;
    this.pricingPolicy = pricingPolicy;
    this.clock = clock;
  }

  @Override public WebhookEventType supportedType() { return WebhookEventType.PARKED; }

  @Override
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public WebhookOutcome process(WebhookRequest request, String idempotencyHeader) {
    String plate = keyService.normalizePlate(request.licensePlate());
    if (request.latitude() == null || request.longitude() == null) {
      throw new InvalidWebhookPayloadException("As coordenadas da vaga são obrigatórias.");
    }
    byte[] fingerprint = keyService.fingerprint(request, plate);
    IdempotencyKey supplied = keyService.fromHeader(idempotencyHeader);
    if (supplied != null) {
      var idempotency = recorder.check(supplied, supportedType(), fingerprint);
      if (idempotency.found()) return idempotency.outcome();
    }

    ParkingSession session = sessionRepository
        .findByPlateAndStatusForUpdate(plate, ParkingSessionStatus.ENTERED)
        .orElse(null);
    ParkingSession activeSession = session != null ? session : sessionRepository
        .findByPlateAndStatusForUpdate(plate, ParkingSessionStatus.PARKED).orElse(null);
    IdempotencyKey key = supplied != null
        ? supplied
        : activeSession != null
            ? keyService.parked(plate, activeSession.getEntryTime(),
                request.latitude(), request.longitude())
            : keyService.parkedWithoutActiveSession(
                plate, request.latitude(), request.longitude());
    var idempotency = recorder.check(key, supportedType(), fingerprint);
    if (idempotency.found()) return idempotency.outcome();
    if (session == null) {
      return recorder.record(key, fingerprint, supportedType(), plate,
          WebhookProcessingResult.REJECTED, 404,
          "Não foi encontrada uma sessão aguardando estacionamento para a placa.");
    }

    var spot = spotRepository
        .findByCoordinatesForUpdate(request.latitude(), request.longitude())
        .orElse(null);
    if (spot == null) {
      return recorder.record(key, fingerprint, supportedType(), plate,
          WebhookProcessingResult.REJECTED, 404,
          "Não foi encontrada uma vaga nas coordenadas informadas.");
    }
    var sector = sectorRepository.findByIdForUpdate(spot.getSector().getId()).orElseThrow();
    if (spot.isOccupied()) {
      return recorder.record(key, fingerprint, supportedType(), plate,
          WebhookProcessingResult.REJECTED, 409, "A vaga informada já está ocupada.");
    }
    long occupied = spotRepository.countBySectorIdAndOccupiedTrue(sector.getId());
    if (occupied >= sector.getMaxCapacity()) {
      return recorder.record(key, fingerprint, supportedType(), plate,
          WebhookProcessingResult.REJECTED, 409, "O setor está lotado.");
    }
    DynamicPrice price = pricingPolicy.calculate(
        sector.getBasePrice(), occupied, sector.getMaxCapacity());
    session.park(sector, spot, clock.instant(), sector.getBasePrice(),
        price.multiplier(), price.hourlyPrice());
    spot.occupy();
    return recorder.record(key, fingerprint, supportedType(), plate,
        WebhookProcessingResult.PROCESSED, 200, "Evento PARKED processado com sucesso.");
  }
}
