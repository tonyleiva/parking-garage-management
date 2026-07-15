package com.tonyleiva.parkinggarage.application.garage;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;

public record ValidatedGarageConfiguration(List<Sector> sectors, List<Spot> spots) {
  public record Sector(
      String code,
      BigDecimal basePrice,
      int maxCapacity,
      LocalTime openHour,
      LocalTime closeHour,
      int durationLimitMinutes) {}

  public record Spot(
      Long externalId,
      String sectorCode,
      BigDecimal latitude,
      BigDecimal longitude,
      boolean occupied) {}
}
