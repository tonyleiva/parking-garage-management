package com.tonyleiva.parkinggarage.application.webhook;

import com.tonyleiva.parkinggarage.domain.session.ParkingSession;
import com.tonyleiva.parkinggarage.domain.session.ParkingSessionStatus;
import com.tonyleiva.parkinggarage.domain.webhook.WebhookEventType;
import com.tonyleiva.parkinggarage.domain.webhook.WebhookProcessingResult;
import com.tonyleiva.parkinggarage.infrastructure.persistence.GarageSectorRepository;
import com.tonyleiva.parkinggarage.infrastructure.persistence.ParkingSessionRepository;
import com.tonyleiva.parkinggarage.infrastructure.persistence.ParkingSpotRepository;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

@Component
public class EntryEventHandler implements WebhookEventHandler {
  private static final List<ParkingSessionStatus> ACTIVE =
      List.of(ParkingSessionStatus.ENTERED, ParkingSessionStatus.PARKED);
  private final IdempotencyKeyService keyService;
  private final WebhookEventRecorder recorder;
  private final ParkingSessionRepository sessionRepository;
  private final ParkingSpotRepository spotRepository;
  private final GarageSectorRepository sectorRepository;

  public EntryEventHandler(
      IdempotencyKeyService keyService,
      WebhookEventRecorder recorder,
      ParkingSessionRepository sessionRepository,
      ParkingSpotRepository spotRepository,
      GarageSectorRepository sectorRepository) {
    this.keyService = keyService;
    this.recorder = recorder;
    this.sessionRepository = sessionRepository;
    this.spotRepository = spotRepository;
    this.sectorRepository = sectorRepository;
  }

  @Override public WebhookEventType supportedType() { return WebhookEventType.ENTRY; }

  @Override
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public WebhookOutcome process(WebhookRequest request, String idempotencyHeader) {
    String plate = keyService.normalizePlate(request.licensePlate());
    if (request.entryTime() == null) {
      throw new InvalidWebhookPayloadException("O horário de entrada é obrigatório.");
    }
    IdempotencyKey key = headerOrFallback(idempotencyHeader, plate, request);
    byte[] fingerprint = keyService.fingerprint(request, plate);
    var idempotency = recorder.check(key, supportedType(), fingerprint);
    if (idempotency.found()) return idempotency.outcome();

    if (sessionRepository.findActiveForUpdate(plate, ACTIVE).isPresent()) {
      return recorder.record(key, fingerprint, supportedType(), plate,
          WebhookProcessingResult.REJECTED, 409, "A placa já possui uma sessão ativa.");
    }
    var sectors = sectorRepository.findAllForUpdateOrderById();
    long occupied = spotRepository.countByOccupiedTrue();
    long entered = sessionRepository.countByStatus(ParkingSessionStatus.ENTERED);
    long totalCapacity = sectors.stream().mapToLong(sector -> sector.getMaxCapacity()).sum();
    if (occupied + entered >= totalCapacity) {
      return recorder.record(key, fingerprint, supportedType(), plate,
          WebhookProcessingResult.REJECTED, 409, "A garagem está cheia.");
    }
    sessionRepository.save(new ParkingSession(plate, request.entryTime()));
    return recorder.record(key, fingerprint, supportedType(), plate,
        WebhookProcessingResult.PROCESSED, 200, "Evento ENTRY processado com sucesso.");
  }

  private IdempotencyKey headerOrFallback(
      String header, String plate, WebhookRequest request) {
    IdempotencyKey supplied = keyService.fromHeader(header);
    return supplied != null ? supplied : keyService.entry(plate, request.entryTime());
  }
}
