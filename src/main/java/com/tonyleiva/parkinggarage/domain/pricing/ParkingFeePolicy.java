package com.tonyleiva.parkinggarage.domain.pricing;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;

public class ParkingFeePolicy {
  private static final Duration FREE_PERIOD = Duration.ofMinutes(30);
  private static final long NANOS_PER_HOUR = Duration.ofHours(1).toNanos();

  public BigDecimal calculate(Instant entryTime, Instant exitTime, BigDecimal hourlyPrice) {
    if (entryTime == null || exitTime == null || hourlyPrice == null || hourlyPrice.signum() < 0) {
      throw new IllegalArgumentException("Os dados para cálculo da cobrança são inválidos.");
    }
    Duration duration = Duration.between(entryTime, exitTime);
    if (duration.isNegative()) {
      throw new IllegalArgumentException(
          "O horário de saída não pode ser anterior ao horário de entrada.");
    }
    if (duration.compareTo(FREE_PERIOD) <= 0) return BigDecimal.ZERO.setScale(4);
    long nanos = duration.toNanos();
    long hours = Math.floorDiv(nanos - 1, NANOS_PER_HOUR) + 1;
    return hourlyPrice.multiply(BigDecimal.valueOf(hours)).setScale(4, RoundingMode.HALF_UP);
  }
}
