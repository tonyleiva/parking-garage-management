package com.tonyleiva.parkinggarage.infrastructure.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class GarageSimulatorClient {
  private static final Logger LOGGER = LoggerFactory.getLogger(GarageSimulatorClient.class);
  private final RestClient restClient;

  public GarageSimulatorClient(RestClient garageRestClient) {
    this.restClient = garageRestClient;
  }

  public GarageConfigurationResponse fetchConfiguration() {
    try {
      GarageConfigurationResponse response =
          restClient.get().uri("/garage").retrieve().body(GarageConfigurationResponse.class);
      if (response == null)
        throw new IllegalStateException("O simulador retornou uma resposta vazia.");
      return response;
    } catch (Exception exception) {
      LOGGER.error("Não foi possível consultar a configuração do simulador.", exception);
      throw new GarageSimulatorException(
          "Não foi possível consultar a configuração do simulador.", exception);
    }
  }
}
