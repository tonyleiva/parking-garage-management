package com.tonyleiva.parkinggarage.infrastructure.config;

import com.tonyleiva.parkinggarage.application.garage.GarageStartupService;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GarageStartupConfiguration {
  @Bean
  ApplicationRunner garageStartupRunner(GarageStartupService service) {
    return arguments -> service.initialize();
  }
}
