package com.tonyleiva.parkinggarage.infrastructure.config;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("garage.simulator")
public record GarageSimulatorProperties(
    @DefaultValue("http://localhost:3000") URI baseUrl,
    @DefaultValue("2s") Duration connectTimeout,
    @DefaultValue("5s") Duration readTimeout) {}
