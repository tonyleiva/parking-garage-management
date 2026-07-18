package com.tonyleiva.parkinggarage.application.webhook;

import com.tonyleiva.parkinggarage.domain.session.ParkingSessionStatus;
import com.tonyleiva.parkinggarage.infrastructure.persistence.ParkingSessionRepository;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
public class WebhookRequestProcessor {
  private final WebhookEventDispatcher dispatcher;
  private final IdempotencyKeyService keyService;
  private final ParkingSessionRepository sessionRepository;
  private final WebhookEventRecorder recorder;

  public WebhookRequestProcessor(
      WebhookEventDispatcher dispatcher,
      IdempotencyKeyService keyService,
      ParkingSessionRepository sessionRepository,
      WebhookEventRecorder recorder) {
    this.dispatcher = dispatcher;
    this.keyService = keyService;
    this.sessionRepository = sessionRepository;
    this.recorder = recorder;
  }

  public WebhookOutcome process(WebhookRequest request, String idempotencyHeader) {
    try {
      return dispatcher.dispatch(request, idempotencyHeader);
    } catch (DataIntegrityViolationException exception) {
      IdempotencyKey key = resolveKey(request, idempotencyHeader);
      String plate = keyService.normalizePlate(request.licensePlate());
      byte[] fingerprint = keyService.fingerprint(request, plate);
      var check = recorder.check(key, request.eventType(), fingerprint);
      if (check.found()) return check.outcome();
      throw exception;
    }
  }

  private IdempotencyKey resolveKey(WebhookRequest request, String header) {
    IdempotencyKey supplied = keyService.fromHeader(header);
    if (supplied != null) return supplied;
    String plate = keyService.normalizePlate(request.licensePlate());
    return switch (request.eventType()) {
      case ENTRY -> keyService.entry(plate, request.entryTime());
      case EXIT -> keyService.exit(plate, request.exitTime());
      case PARKED -> {
        var active = sessionRepository.findActiveForUpdate(
            plate, List.of(ParkingSessionStatus.ENTERED, ParkingSessionStatus.PARKED));
        yield active
            .map(session -> keyService.parked(
                plate, session.getEntryTime(), request.latitude(), request.longitude()))
            .orElseGet(() -> keyService.parkedWithoutActiveSession(
                plate, request.latitude(), request.longitude()));
      }
    };
  }
}
