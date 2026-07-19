package com.tonyleiva.parkinggarage.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("garage.startup")
public record GarageStartupProperties(@DefaultValue("false") boolean resetFromSimulator) {}
