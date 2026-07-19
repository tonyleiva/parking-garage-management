package com.tonyleiva.parkinggarage.domain.pricing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ParkingFeePolicyTest {
  private final ParkingFeePolicy policy = new ParkingFeePolicy();
  private final Instant entry = Instant.parse("2025-01-01T12:00:00Z");

  @Test
  void shouldChargeZeroThroughThirtyMinutes() {
    assertAmount("2025-01-01T12:30:00Z", "0.0000");
  }

  @Test
  void shouldChargeOneHourAtThirtyOneMinutes() {
    assertAmount("2025-01-01T12:31:00Z", "40.5000");
  }

  @Test
  void shouldChargeOneHourAtExactlySixtyMinutes() {
    assertAmount("2025-01-01T13:00:00Z", "40.5000");
  }

  @Test
  void shouldChargeTwoHoursForARealFractionAboveSixtyMinutes() {
    assertAmount("2025-01-01T13:00:00.001Z", "81.0000");
  }

  @Test
  void shouldChargeTwoHoursAtSixtyOneMinutes() {
    assertAmount("2025-01-01T13:01:00Z", "81.0000");
  }

  @Test
  void shouldChargeTwoHoursAtSeventyMinutes() {
    assertAmount("2025-01-01T13:10:00Z", "81.0000");
  }

  @Test
  void shouldRejectExitBeforeEntry() {
    assertThatThrownBy(() -> policy.calculate(
        entry, entry.minusSeconds(1), new BigDecimal("40.5000")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("O horário de saída não pode ser anterior ao horário de entrada.");
  }

  private void assertAmount(String exit, String expected) {
    assertThat(policy.calculate(entry, Instant.parse(exit), new BigDecimal("40.5000")))
        .isEqualByComparingTo(expected);
  }
}
