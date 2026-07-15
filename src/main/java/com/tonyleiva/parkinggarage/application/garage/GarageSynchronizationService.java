package com.tonyleiva.parkinggarage.application.garage;

import com.tonyleiva.parkinggarage.infrastructure.client.GarageConfigurationResponse;
import com.tonyleiva.parkinggarage.infrastructure.client.GarageSimulatorClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class GarageSynchronizationService {
  private static final Logger LOGGER = LoggerFactory.getLogger(GarageSynchronizationService.class);
  private final GarageSimulatorClient client;
  private final GarageConfigurationValidator validator;
  private final GaragePersistenceService persistenceService;

  public GarageSynchronizationService(
      GarageSimulatorClient client,
      GarageConfigurationValidator validator,
      GaragePersistenceService persistenceService) {
    this.client = client;
    this.validator = validator;
    this.persistenceService = persistenceService;
  }

  public void synchronize() {
    LOGGER.info("Iniciando a sincronização da configuração da garagem.");
    try {
      var response = client.fetchConfiguration();
      LOGGER.info(
          "Configuração recebida com {} setores e {} vagas.",
          response.garage().size(),
          response.spots().size());
      logReceivedSectors(response);
      var configuration = validator.validate(response);
      LOGGER.info("Configuração da garagem validada com sucesso.");
      persistenceService.synchronize(configuration);
      LOGGER.info(
          "Sincronização da configuração da garagem concluída com {} setores e {} vagas.",
          configuration.sectors().size(),
          configuration.spots().size());
    } catch (RuntimeException exception) {
      LOGGER.error(
          "Falha na configuração da garagem. A inicialização será interrompida.", exception);
      throw exception;
    }
  }

  private void logReceivedSectors(GarageConfigurationResponse response) {
    if (!LOGGER.isDebugEnabled() || response.garage() == null) {
      return;
    }

    response
        .garage()
        .forEach(
            sector ->
                LOGGER.debug(
                    "Setor recebido: código={}, preçoBase={}, capacidade={}, abertura={},"
                        + " fechamento={}, limitePermanênciaMinutos={}.",
                    sector.sector(),
                    sector.basePrice(),
                    sector.maxCapacity(),
                    sector.openHour(),
                    sector.closeHour(),
                    sector.durationLimitMinutes()));
  }
}
