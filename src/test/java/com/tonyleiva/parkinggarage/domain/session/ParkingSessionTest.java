package com.tonyleiva.parkinggarage.domain.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.tonyleiva.parkinggarage.domain.sector.GarageSector;
import com.tonyleiva.parkinggarage.domain.spot.ParkingSpot;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ParkingSessionTest {
  @Test
  void shouldFollowEnteredParkedFinishedTransition() {
    var session = new ParkingSession("ZUL0001", Instant.parse("2025-01-01T12:00:00Z"));
    session.park(mock(GarageSector.class), mock(ParkingSpot.class),
        Instant.parse("2025-01-01T12:01:00Z"), new BigDecimal("40.0000"),
        new BigDecimal("0.90"), new BigDecimal("36.0000"));
    session.finish(Instant.parse("2025-01-01T13:00:00Z"), new BigDecimal("36.0000"));

    assertThat(session.getStatus()).isEqualTo(ParkingSessionStatus.FINISHED);
    assertThat(session.getAmount()).isEqualByComparingTo("36.0000");
  }

  @Test
  void shouldRejectInvalidTransitionWithPortugueseMessage() {
    var session = new ParkingSession("ZUL0001", Instant.parse("2025-01-01T12:00:00Z"));
    assertThatThrownBy(() -> session.finish(
        Instant.parse("2025-01-01T13:00:00Z"), BigDecimal.ZERO))
        .isInstanceOf(InvalidSessionTransitionException.class)
        .hasMessageContaining("Não é possível finalizar");
  }
}
