package com.tonyleiva.parkinggarage.application.garage;

import com.tonyleiva.parkinggarage.domain.sector.GarageSector;
import com.tonyleiva.parkinggarage.domain.spot.ParkingSpot;
import com.tonyleiva.parkinggarage.infrastructure.persistence.GarageSectorRepository;
import com.tonyleiva.parkinggarage.infrastructure.persistence.ParkingSpotRepository;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GaragePersistenceService {
  private final GarageSectorRepository sectorRepository;
  private final ParkingSpotRepository spotRepository;

  public GaragePersistenceService(
      GarageSectorRepository sectorRepository, ParkingSpotRepository spotRepository) {
    this.sectorRepository = sectorRepository;
    this.spotRepository = spotRepository;
  }

  @Transactional
  public void synchronize(ValidatedGarageConfiguration configuration) {
    removeObsoleteSpots(configuration);

    Map<String, GarageSector> sectors = new HashMap<>();
    for (var data : configuration.sectors()) {
      GarageSector sector =
          sectorRepository
              .findByCode(data.code())
              .orElseGet(
                  () ->
                      new GarageSector(
                          data.code(),
                          data.basePrice(),
                          data.maxCapacity(),
                          data.openHour(),
                          data.closeHour(),
                          data.durationLimitMinutes()));
      sector.update(
          data.basePrice(),
          data.maxCapacity(),
          data.openHour(),
          data.closeHour(),
          data.durationLimitMinutes());
      sectors.put(data.code(), sectorRepository.save(sector));
    }

    for (var data : configuration.spots()) {
      ParkingSpot spot =
          spotRepository
              .findByExternalId(data.externalId())
              .orElseGet(
                  () ->
                      new ParkingSpot(
                          data.externalId(),
                          sectors.get(data.sectorCode()),
                          data.latitude(),
                          data.longitude(),
                          data.occupied()));
      spot.update(
          sectors.get(data.sectorCode()), data.latitude(), data.longitude(), data.occupied());
      spotRepository.save(spot);
    }

    spotRepository.flush();
    removeObsoleteSectors(configuration);
  }

  private void removeObsoleteSpots(ValidatedGarageConfiguration configuration) {
    Set<Long> currentExternalIds = new HashSet<>();
    configuration.spots().forEach(spot -> currentExternalIds.add(spot.externalId()));
    var obsoleteSpots =
        spotRepository.findAll().stream()
            .filter(spot -> !currentExternalIds.contains(spot.getExternalId()))
            .toList();

    if (obsoleteSpots.isEmpty()) {
      return;
    }

    try {
      spotRepository.deleteAllInBatch(obsoleteSpots);
      spotRepository.flush();
    } catch (DataIntegrityViolationException exception) {
      throw reconciliationFailure("vagas", exception);
    }
  }

  private void removeObsoleteSectors(ValidatedGarageConfiguration configuration) {
    Set<String> currentCodes = new HashSet<>();
    configuration.sectors().forEach(sector -> currentCodes.add(sector.code()));
    var obsoleteSectors =
        sectorRepository.findAll().stream()
            .filter(sector -> !currentCodes.contains(sector.getCode()))
            .toList();

    if (obsoleteSectors.isEmpty()) {
      return;
    }

    try {
      sectorRepository.deleteAllInBatch(obsoleteSectors);
      sectorRepository.flush();
    } catch (DataIntegrityViolationException exception) {
      throw reconciliationFailure("setores", exception);
    }
  }

  private GarageReconciliationException reconciliationFailure(
      String recordType, DataIntegrityViolationException cause) {
    return new GarageReconciliationException(
        "Não foi possível remover registros obsoletos do tipo "
            + recordType
            + " durante a sincronização porque existem referências no banco de dados.",
        cause);
  }
}
