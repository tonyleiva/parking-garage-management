package com.tonyleiva.parkinggarage.application.garage;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;

public record GarageStateSummary(
    long sectors, long spots, long occupied, List<SectorSummary> sectorSummaries) {
  public long available() { return spots - occupied; }

  public record SectorSummary(
      String code,
      int capacity,
      long occupied,
      BigDecimal basePrice,
      LocalTime openHour,
      LocalTime closeHour) {
    public long available() { return capacity - occupied; }
  }
}
