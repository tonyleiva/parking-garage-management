package com.tonyleiva.parkinggarage.application.garage;

import com.tonyleiva.parkinggarage.infrastructure.client.GarageConfigurationResponse;
import com.tonyleiva.parkinggarage.infrastructure.client.GarageSimulatorClient;
import com.tonyleiva.parkinggarage.infrastructure.config.GarageStartupProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class GarageStartupService {
  private static final Logger LOGGER = LoggerFactory.getLogger(GarageStartupService.class);
  private final GarageSimulatorClient client;
  private final GarageConfigurationValidator validator;
  private final GaragePersistenceService persistenceService;
  private final GarageStartupProperties properties;

  public GarageStartupService(
      GarageSimulatorClient client,
      GarageConfigurationValidator validator,
      GaragePersistenceService persistenceService,
      GarageStartupProperties properties) {
    this.client = client;
    this.validator = validator;
    this.persistenceService = persistenceService;
    this.properties = properties;
  }

  public void initialize() {
    GarageStartupOrigin origin;
    GarageStateSummary summary;
    if (properties.resetFromSimulator() || persistenceService.isEmpty()) {
      ValidatedGarageConfiguration configuration = fetchAndValidate();
      summary = persistenceService.replaceAll(configuration);
      origin = GarageStartupOrigin.SIMULATOR;
    } else {
      persistenceService.validateStoredConfiguration();
      summary = persistenceService.summarize();
      origin = GarageStartupOrigin.DATABASE;
    }
    logSummary(origin, summary);
  }

  private ValidatedGarageConfiguration fetchAndValidate() {
    LOGGER.info("Consultando a configuração da garagem no simulador.");
    GarageConfigurationResponse response = client.fetchConfiguration();
    ValidatedGarageConfiguration configuration = validator.validate(response);
    LOGGER.info("Configuração da garagem recebida e validada com sucesso.");
    return configuration;
  }

  private void logSummary(GarageStartupOrigin origin, GarageStateSummary summary) {
    LOGGER.info(
        "Inicialização da garagem concluída: origem={}, setores={}, vagas={}, ocupadas={}, livres={}.",
        origin.logValue(), summary.sectors(), summary.spots(), summary.occupied(), summary.available());
    if (LOGGER.isDebugEnabled()) {
      summary.sectorSummaries().forEach(
          sector -> LOGGER.debug(
              "Estado inicial do setor: código={}, capacidade={}, ocupadas={}, livres={},"
                  + " preçoBase={}, abertura={}, fechamento={}.",
              sector.code(), sector.capacity(), sector.occupied(), sector.available(),
              sector.basePrice(), sector.openHour(), sector.closeHour()));
    }
  }
}
