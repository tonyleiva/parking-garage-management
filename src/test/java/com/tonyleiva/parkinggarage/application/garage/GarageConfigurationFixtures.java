package com.tonyleiva.parkinggarage.application.garage;

import com.tonyleiva.parkinggarage.infrastructure.client.GarageConfigurationResponse;
import java.math.BigDecimal;
import java.util.List;

final class GarageConfigurationFixtures {
  private GarageConfigurationFixtures() {}

  static GarageConfigurationResponse valid() {
    return new GarageConfigurationResponse(
        List.of(
            new GarageConfigurationResponse.SectorResponse(
                "A", new BigDecimal("40.5000"), 2, "00:00", "23:59", 1440)),
        List.of(
            new GarageConfigurationResponse.SpotResponse(
                1L, "A", new BigDecimal("-23.56168400"), new BigDecimal("-46.65598100"), false),
            new GarageConfigurationResponse.SpotResponse(
                2L, "A", new BigDecimal("-23.56168501"), new BigDecimal("-46.65598201"), true)));
  }
}
