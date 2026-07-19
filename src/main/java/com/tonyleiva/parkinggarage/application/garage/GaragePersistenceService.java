package com.tonyleiva.parkinggarage.application.garage;

import com.tonyleiva.parkinggarage.domain.sector.GarageSector;
import com.tonyleiva.parkinggarage.domain.spot.ParkingSpot;
import com.tonyleiva.parkinggarage.infrastructure.persistence.GarageSectorRepository;
import com.tonyleiva.parkinggarage.infrastructure.persistence.ParkingSessionRepository;
import com.tonyleiva.parkinggarage.infrastructure.persistence.ParkingSpotRepository;
import com.tonyleiva.parkinggarage.infrastructure.persistence.ProcessedWebhookEventRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GaragePersistenceService {
  private final GarageSectorRepository sectorRepository;
  private final ParkingSpotRepository spotRepository;
  private final ParkingSessionRepository sessionRepository;
  private final ProcessedWebhookEventRepository eventRepository;

  public GaragePersistenceService(
      GarageSectorRepository sectorRepository,
      ParkingSpotRepository spotRepository,
      ParkingSessionRepository sessionRepository,
      ProcessedWebhookEventRepository eventRepository) {
    this.sectorRepository = sectorRepository;
    this.spotRepository = spotRepository;
    this.sessionRepository = sessionRepository;
    this.eventRepository = eventRepository;
  }

  @Transactional(readOnly = true)
  public boolean isEmpty() {
    return sectorRepository.count() == 0 && spotRepository.count() == 0;
  }

  @Transactional(readOnly = true)
  public void validateStoredConfiguration() {
    long sectors = sectorRepository.count();
    long spots = spotRepository.count();
    if (sectors == 0 || spots == 0) {
      throw new GarageReconciliationException(
          "O estado persistido da garagem está incompleto e não pode ser retomado.", null);
    }
  }

  @Transactional
  public GarageStateSummary replaceAll(ValidatedGarageConfiguration configuration) {
    try {
      eventRepository.deleteAllInBatch();
      sessionRepository.deleteAllInBatch();
      spotRepository.deleteAllInBatch();
      sectorRepository.deleteAllInBatch();
      eventRepository.flush();
      sessionRepository.flush();
      spotRepository.flush();
      sectorRepository.flush();

      Map<String, GarageSector> sectors = new HashMap<>();
      for (var data : configuration.sectors()) {
        GarageSector sector =
            sectorRepository.save(
                new GarageSector(
                    data.code(),
                    data.basePrice(),
                    data.maxCapacity(),
                    data.openHour(),
                    data.closeHour(),
                    data.durationLimitMinutes()));
        sectors.put(data.code(), sector);
      }
      for (var data : configuration.spots()) {
        spotRepository.save(
            new ParkingSpot(
                data.externalId(),
                sectors.get(data.sectorCode()),
                data.latitude(),
                data.longitude(),
                data.occupied()));
      }
      spotRepository.flush();
      return summarizeInternal();
    } catch (DataIntegrityViolationException exception) {
      throw new GarageReconciliationException(
          "Não foi possível substituir o estado da garagem; nenhuma alteração foi aplicada.",
          exception);
    }
  }

  @Transactional
  public GarageStateSummary synchronize(ValidatedGarageConfiguration configuration) {
    return replaceAll(configuration);
  }

  @Transactional(readOnly = true)
  public GarageStateSummary summarize() {
    return summarizeInternal();
  }

  private GarageStateSummary summarizeInternal() {
    var sectors = sectorRepository.findAll();
    var sectorSummaries = new ArrayList<GarageStateSummary.SectorSummary>();
    long occupied = 0;
    for (GarageSector sector : sectors) {
      long sectorOccupied = spotRepository.countBySectorIdAndOccupiedTrue(sector.getId());
      occupied += sectorOccupied;
      sectorSummaries.add(
          new GarageStateSummary.SectorSummary(
              sector.getCode(),
              sector.getMaxCapacity(),
              sectorOccupied,
              sector.getBasePrice(),
              sector.getOpenHour(),
              sector.getCloseHour()));
    }
    return new GarageStateSummary(sectors.size(), spotRepository.count(), occupied, sectorSummaries);
  }
}
