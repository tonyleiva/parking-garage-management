package com.tonyleiva.parkinggarage.domain.pricing;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class DynamicPricingPolicy {
  public DynamicPrice calculate(BigDecimal basePrice, long occupied, long capacity) {
    if (basePrice == null || basePrice.signum() < 0 || capacity <= 0 || occupied < 0) {
      throw new IllegalArgumentException("Os dados para cálculo do preço dinâmico são inválidos.");
    }
    if (occupied >= capacity) {
      throw new IllegalStateException("O setor está lotado.");
    }
    BigDecimal ratio = BigDecimal.valueOf(occupied)
        .divide(BigDecimal.valueOf(capacity), 8, RoundingMode.HALF_UP);
    BigDecimal multiplier;
    if (ratio.compareTo(new BigDecimal("0.25")) < 0) multiplier = new BigDecimal("0.90");
    else if (ratio.compareTo(new BigDecimal("0.50")) < 0) multiplier = new BigDecimal("1.00");
    else if (ratio.compareTo(new BigDecimal("0.75")) < 0) multiplier = new BigDecimal("1.10");
    else multiplier = new BigDecimal("1.25");
    BigDecimal hourlyPrice = basePrice.multiply(multiplier).setScale(4, RoundingMode.HALF_UP);
    return new DynamicPrice(multiplier.setScale(2, RoundingMode.UNNECESSARY), hourlyPrice);
  }
}
