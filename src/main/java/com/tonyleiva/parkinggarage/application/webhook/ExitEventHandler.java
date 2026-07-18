package com.tonyleiva.parkinggarage.application.webhook;

import com.tonyleiva.parkinggarage.domain.pricing.ParkingFeePolicy;
import com.tonyleiva.parkinggarage.domain.session.ParkingSessionStatus;
import com.tonyleiva.parkinggarage.domain.webhook.WebhookEventType;
import com.tonyleiva.parkinggarage.domain.webhook.WebhookProcessingResult;
import com.tonyleiva.parkinggarage.infrastructure.persistence.ParkingSessionRepository;
import com.tonyleiva.parkinggarage.infrastructure.persistence.ParkingSpotRepository;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ExitEventHandler implements WebhookEventHandler {
  private final IdempotencyKeyService keyService;
  private final WebhookEventRecorder recorder;
  private final ParkingSessionRepository sessionRepository;
  private final ParkingSpotRepository spotRepository;
  private final ParkingFeePolicy feePolicy;

  public ExitEventHandler(
      IdempotencyKeyService keyService,
      WebhookEventRecorder recorder,
      ParkingSessionRepository sessionRepository,
      ParkingSpotRepository spotRepository,
      ParkingFeePolicy feePolicy) {
    this.keyService = keyService;
    this.recorder = recorder;
    this.sessionRepository = sessionRepository;
    this.spotRepository = spotRepository;
    this.feePolicy = feePolicy;
  }

  @Override public WebhookEventType supportedType() { return WebhookEventType.EXIT; }

  @Override
  @Transactional
  public WebhookOutcome process(WebhookRequest request, String idempotencyHeader) {
    String plate = keyService.normalizePlate(request.licensePlate());
    if (request.exitTime() == null) {
      throw new InvalidWebhookPayloadException("O horário de saída é obrigatório.");
    }
    IdempotencyKey supplied = keyService.fromHeader(idempotencyHeader);
    IdempotencyKey key = supplied != null ? supplied : keyService.exit(plate, request.exitTime());
    byte[] fingerprint = keyService.fingerprint(request, plate);
    var idempotency = recorder.check(key, supportedType(), fingerprint);
    if (idempotency.found()) return idempotency.outcome();

    var session = sessionRepository
        .findByPlateAndStatusForUpdate(plate, ParkingSessionStatus.PARKED).orElse(null);
    if (session == null) {
      boolean entered = sessionRepository.findActiveForUpdate(
          plate, List.of(ParkingSessionStatus.ENTERED, ParkingSessionStatus.PARKED))
          .map(active -> active.getStatus() == ParkingSessionStatus.ENTERED).orElse(false);
      int status = entered ? 409 : 404;
      String message = entered
          ? "A saída não pode ser processada antes do evento PARKED."
          : "Não foi encontrada uma sessão estacionada para a placa.";
      return recorder.record(key, fingerprint, supportedType(), plate,
          WebhookProcessingResult.REJECTED, status, message);
    }
    var spot = spotRepository.findByIdForUpdate(session.getSpot().getId()).orElse(null);
    if (spot == null) {
      return recorder.record(key, fingerprint, supportedType(), plate,
          WebhookProcessingResult.REJECTED, 404,
          "A vaga associada à sessão não foi encontrada.");
    }
    if (request.exitTime().isBefore(session.getEntryTime())) {
      return recorder.record(key, fingerprint, supportedType(), plate,
          WebhookProcessingResult.REJECTED, 409,
          "O horário de saída não pode ser anterior ao horário de entrada.");
    }
    var amount = feePolicy.calculate(
        session.getEntryTime(), request.exitTime(), session.getHourlyPrice());
    spot.release();
    session.finish(request.exitTime(), amount);
    return recorder.record(key, fingerprint, supportedType(), plate,
        WebhookProcessingResult.PROCESSED, 200, "Evento EXIT processado com sucesso.");
  }
}
