package com.tonyleiva.parkinggarage.application.revenue;

import com.tonyleiva.parkinggarage.domain.session.ParkingSessionStatus;
import com.tonyleiva.parkinggarage.infrastructure.persistence.GarageSectorRepository;
import com.tonyleiva.parkinggarage.infrastructure.persistence.ParkingSessionRepository;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RevenueService {
  private static final String CURRENCY = "BRL";
  private static final ZoneId REVENUE_ZONE = ZoneId.of("America/Sao_Paulo");
  private static final DateTimeFormatter DATE_FORMATTER =
      DateTimeFormatter.ofPattern("uuuu-MM-dd").withResolverStyle(ResolverStyle.STRICT);
  private final GarageSectorRepository sectorRepository;
  private final ParkingSessionRepository sessionRepository;
  private final Clock clock;

  public RevenueService(
      GarageSectorRepository sectorRepository,
      ParkingSessionRepository sessionRepository,
      Clock clock) {
    this.sectorRepository = sectorRepository;
    this.sessionRepository = sessionRepository;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public RevenueResult calculate(String requestedDate, String requestedSector) {
    LocalDate date = parseDate(requestedDate);
    String sectorCode = validateSector(requestedSector);
    if (sectorRepository.findByCode(sectorCode).isEmpty()) {
      throw new SectorNotFoundException(sectorCode);
    }

    Instant start = date.atStartOfDay(REVENUE_ZONE).toInstant();
    Instant end = date.plusDays(1).atStartOfDay(REVENUE_ZONE).toInstant();
    var amount = sessionRepository.sumAmountBySectorAndStatusAndExitTime(
        sectorCode, ParkingSessionStatus.FINISHED, start, end);
    return new RevenueResult(amount.setScale(2, RoundingMode.HALF_UP), CURRENCY, clock.instant());
  }

  private LocalDate parseDate(String value) {
    if (value == null || value.isBlank()) {
      throw invalidDate();
    }
    try {
      return LocalDate.parse(value, DATE_FORMATTER);
    } catch (DateTimeException exception) {
      throw invalidDate();
    }
  }

  private String validateSector(String value) {
    if (value == null || value.isBlank()) {
      throw new InvalidRevenueRequestException("O setor é obrigatório.");
    }
    return value.trim();
  }

  private InvalidRevenueRequestException invalidDate() {
    return new InvalidRevenueRequestException(
        "A data deve estar no formato yyyy-MM-dd, por exemplo: 2026-06-19.");
  }
}
