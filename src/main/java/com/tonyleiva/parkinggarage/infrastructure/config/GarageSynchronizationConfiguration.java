package com.tonyleiva.parkinggarage.infrastructure.config;

import com.tonyleiva.parkinggarage.application.garage.GarageSynchronizationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GarageSynchronizationConfiguration {
  private static final Logger LOGGER =
      LoggerFactory.getLogger(GarageSynchronizationConfiguration.class);

  @Bean
  @ConditionalOnProperty(
      name = "garage.synchronization.enabled",
      havingValue = "true",
      matchIfMissing = true)
  ApplicationRunner garageSynchronizationRunner(GarageSynchronizationService service) {
    return arguments -> {
      try {
        service.synchronize();
      } catch (RuntimeException exception) {
        LOGGER.error("Inicialização interrompida devido a uma configuração inválida da garagem.");
        throw exception;
      }
    };
  }
}
