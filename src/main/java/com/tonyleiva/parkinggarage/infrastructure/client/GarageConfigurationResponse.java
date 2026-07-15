package com.tonyleiva.parkinggarage.infrastructure.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.List;

public record GarageConfigurationResponse(List<SectorResponse> garage, List<SpotResponse> spots) {
  public record SectorResponse(
      String sector,
      @JsonProperty("base_price") BigDecimal basePrice,
      @JsonProperty("max_capacity") Integer maxCapacity,
      @JsonProperty("open_hour") String openHour,
      @JsonProperty("close_hour") String closeHour,
      @JsonProperty("duration_limit_minutes") Integer durationLimitMinutes) {}

  public record SpotResponse(
      Long id, String sector, BigDecimal lat, BigDecimal lng, Boolean occupied) {}
}
