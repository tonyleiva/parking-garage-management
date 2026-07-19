package com.tonyleiva.parkinggarage.application.revenue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tonyleiva.parkinggarage.domain.sector.GarageSector;
import com.tonyleiva.parkinggarage.domain.session.ParkingSessionStatus;
import com.tonyleiva.parkinggarage.infrastructure.persistence.GarageSectorRepository;
import com.tonyleiva.parkinggarage.infrastructure.persistence.ParkingSessionRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class RevenueServiceTest {
  @Test
  void shouldConvertTheSaoPauloDayToAStartInclusiveAndEndExclusiveInstantRange() {
    var sectors = mock(GarageSectorRepository.class);
    var sessions = mock(ParkingSessionRepository.class);
    when(sectors.findByCode("A")).thenReturn(Optional.of(mock(GarageSector.class)));
    when(sessions.sumAmountBySectorAndStatusAndExitTime(
        eq("A"), eq(ParkingSessionStatus.FINISHED), any(), any()))
        .thenReturn(BigDecimal.ZERO);
    var service = new RevenueService(
        sectors,
        sessions,
        Clock.fixed(Instant.parse("2025-01-01T12:00:00Z"), ZoneOffset.UTC));
    var start = ArgumentCaptor.forClass(Instant.class);
    var end = ArgumentCaptor.forClass(Instant.class);

    service.calculate("2025-01-01", "A");

    verify(sessions).sumAmountBySectorAndStatusAndExitTime(
        eq("A"), eq(ParkingSessionStatus.FINISHED), start.capture(), end.capture());
    assertThat(start.getValue()).isEqualTo(Instant.parse("2025-01-01T03:00:00Z"));
    assertThat(end.getValue()).isEqualTo(Instant.parse("2025-01-02T03:00:00Z"));
  }
}
