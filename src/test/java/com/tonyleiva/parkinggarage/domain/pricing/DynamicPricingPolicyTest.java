package com.tonyleiva.parkinggarage.domain.pricing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class DynamicPricingPolicyTest {
  private final DynamicPricingPolicy policy = new DynamicPricingPolicy();

  @Test
  void shouldApplyAllOccupancyBandsBeforeTheNewSpot() {
    assertPrice(0, 4, "0.90", "36.4500");
    assertPrice(1, 4, "1.00", "40.5000");
    assertPrice(2, 4, "1.10", "44.5500");
    assertPrice(3, 4, "1.25", "50.6250");
  }

  @Test
  void shouldRoundHourlyPriceExplicitly() {
    var price = policy.calculate(new BigDecimal("10.12345"), 0, 10);
    assertThat(price.hourlyPrice()).isEqualByComparingTo("9.1111");
  }

  @Test
  void shouldRejectAFullSector() {
    assertThatThrownBy(() -> policy.calculate(BigDecimal.TEN, 4, 4))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("O setor está lotado.");
  }

  private void assertPrice(long occupied, long capacity, String multiplier, String hourlyPrice) {
    var result = policy.calculate(new BigDecimal("40.5000"), occupied, capacity);
    assertThat(result.multiplier()).isEqualByComparingTo(multiplier);
    assertThat(result.hourlyPrice()).isEqualByComparingTo(hourlyPrice);
  }
}
