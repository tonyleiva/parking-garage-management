package com.tonyleiva.parkinggarage.application.garage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tonyleiva.parkinggarage.application.revenue.RevenueService;
import com.tonyleiva.parkinggarage.application.webhook.IdempotencyKeyService;
import com.tonyleiva.parkinggarage.application.webhook.WebhookRequest;
import com.tonyleiva.parkinggarage.application.webhook.WebhookRequestProcessor;
import com.tonyleiva.parkinggarage.domain.session.ParkingSession;
import com.tonyleiva.parkinggarage.domain.session.ParkingSessionStatus;
import com.tonyleiva.parkinggarage.domain.webhook.WebhookEventType;
import com.tonyleiva.parkinggarage.infrastructure.client.GarageSimulatorClient;
import com.tonyleiva.parkinggarage.infrastructure.client.GarageConfigurationResponse;
import com.tonyleiva.parkinggarage.infrastructure.config.GarageStartupProperties;
import com.tonyleiva.parkinggarage.infrastructure.persistence.GarageSectorRepository;
import com.tonyleiva.parkinggarage.infrastructure.persistence.ParkingSessionRepository;
import com.tonyleiva.parkinggarage.infrastructure.persistence.ParkingSpotRepository;
import com.tonyleiva.parkinggarage.infrastructure.persistence.ProcessedWebhookEventRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

@Testcontainers
@SpringBootTest(properties = {
    "spring.flyway.enabled=true",
    "spring.jpa.hibernate.ddl-auto=validate"
})
class GarageSynchronizationIntegrationTest {
  private static final Instant FIXED_INSTANT = Instant.parse("2025-01-03T12:00:00Z");
  private static final Clock FIXED_CLOCK = Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC);

  @Container static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4");

  @DynamicPropertySource
  static void databaseProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
    registry.add("spring.datasource.username", MYSQL::getUsername);
    registry.add("spring.datasource.password", MYSQL::getPassword);
  }

  @Autowired GarageConfigurationValidator validator;

  @Autowired GaragePersistenceService persistenceService;

  @Autowired GarageSectorRepository sectorRepository;

  @Autowired ParkingSpotRepository spotRepository;

  @Autowired ParkingSessionRepository sessionRepository;

  @Autowired ProcessedWebhookEventRepository eventRepository;

  @Autowired WebhookRequestProcessor webhookProcessor;

  @Autowired IdempotencyKeyService idempotencyKeyService;

  @Autowired RevenueService revenueService;

  @Autowired JdbcTemplate jdbcTemplate;

  @MockitoBean GarageStartupService startupService;

  @MockitoBean Clock clock;

  @BeforeEach
  void cleanDatabase() {
    when(clock.instant()).thenReturn(FIXED_CLOCK.instant());
    jdbcTemplate.execute("DROP TABLE IF EXISTS spot_reference");
    eventRepository.deleteAll();
    sessionRepository.deleteAll();
    spotRepository.deleteAll();
    sectorRepository.deleteAll();
  }

  @Test
  void shouldApplyAllMigrations() {
    assertThat(jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM flyway_schema_history WHERE success = TRUE", Integer.class))
        .isEqualTo(3);
    assertThat(jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM information_schema.columns"
            + " WHERE table_schema = DATABASE() AND table_name = 'parking_session'",
        Integer.class)).isEqualTo(15);
    assertThat(jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM information_schema.columns"
            + " WHERE table_schema = DATABASE()"
            + " AND table_name = 'processed_webhook_event'"
            + " AND column_name IN ('idempotency_hash', 'request_fingerprint')"
            + " AND data_type = 'binary' AND character_octet_length = 32"
            + " AND is_nullable = 'NO'",
        Integer.class)).isEqualTo(2);
  }

  @Test
  void shouldProcessEntryParkedExitAndKeepFinancialValuesExact() {
    persistenceService.replaceAll(validator.validate(GarageConfigurationFixtures.valid()));
    Instant entry = Instant.parse("2025-01-01T12:00:00Z");

    var entryOutcome = webhookProcessor.process(new WebhookRequest(
        "zul0001", entry, null, null, null, WebhookEventType.ENTRY), null);
    var parkedRequest = new WebhookRequest(
        "ZUL0001", null, null, new BigDecimal("-23.561684"),
        new BigDecimal("-46.655981"), WebhookEventType.PARKED);
    var parkedOutcome = webhookProcessor.process(parkedRequest, null);
    var parkedDuplicate = webhookProcessor.process(parkedRequest, null);
    var exitRequest = new WebhookRequest(
        "ZUL0001", null, Instant.parse("2025-01-01T13:10:00Z"), null, null,
        WebhookEventType.EXIT);
    var exitOutcome = webhookProcessor.process(exitRequest, "exit-operation-1");
    var duplicate = webhookProcessor.process(exitRequest, "exit-operation-1");

    var session = sessionRepository.findAll().getFirst();
    assertThat(entryOutcome.httpStatus()).isEqualTo(200);
    assertThat(parkedOutcome.httpStatus()).isEqualTo(200);
    assertThat(parkedDuplicate.duplicate()).isTrue();
    assertThat(exitOutcome.httpStatus()).isEqualTo(200);
    assertThat(duplicate.duplicate()).isTrue();
    assertThat(session.getStatus()).isEqualTo(ParkingSessionStatus.FINISHED);
    assertThat(session.getBasePrice()).isEqualByComparingTo("40.5000");
    assertThat(session.getPriceMultiplier()).isEqualByComparingTo("1.10");
    assertThat(session.getHourlyPrice()).isEqualByComparingTo("44.5500");
    assertThat(session.getAmount()).isEqualByComparingTo("89.1000");
    assertThat(spotRepository.findByExternalId(1L).orElseThrow().isOccupied()).isFalse();
    assertThat(eventRepository.count()).isEqualTo(3);
  }

  @Test
  void shouldEnforceOnlyOneActiveSessionPerPlateAndAllowFinishedHistory() {
    sessionRepository.saveAndFlush(
        new ParkingSession("ZUL0001", Instant.parse("2025-01-01T10:00:00Z")));
    assertThatThrownBy(() -> sessionRepository.saveAndFlush(
        new ParkingSession("ZUL0001", Instant.parse("2025-01-01T11:00:00Z"))))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);

    cleanDatabase();
    jdbcTemplate.update(
        "INSERT INTO parking_session"
            + " (license_plate, entry_time, exit_time, amount, status, created_at, updated_at)"
            + " VALUES (?, ?, ?, ?, 'FINISHED', NOW(6), NOW(6)),"
            + " (?, ?, ?, ?, 'FINISHED', NOW(6), NOW(6))",
        "ZUL0001", java.sql.Timestamp.from(Instant.parse("2025-01-01T10:00:00Z")),
        java.sql.Timestamp.from(Instant.parse("2025-01-01T11:00:00Z")), BigDecimal.ZERO,
        "ZUL0001", java.sql.Timestamp.from(Instant.parse("2025-01-02T10:00:00Z")),
        java.sql.Timestamp.from(Instant.parse("2025-01-02T11:00:00Z")), BigDecimal.ZERO);
    assertThat(sessionRepository.count()).isEqualTo(2);
  }

  @Test
  void shouldPersistRejectedEventAndRepeatSameResult() {
    persistenceService.replaceAll(validator.validate(GarageConfigurationFixtures.valid()));
    var firstRequest = new WebhookRequest(
        "ZUL0001", Instant.parse("2025-01-01T12:00:00Z"), null, null, null,
        WebhookEventType.ENTRY);
    webhookProcessor.process(firstRequest, "first-entry");
    var conflictRequest = new WebhookRequest(
        "ZUL0001", Instant.parse("2025-01-01T12:05:00Z"), null, null, null,
        WebhookEventType.ENTRY);

    var rejected = webhookProcessor.process(conflictRequest, "conflicting-entry");
    var duplicate = webhookProcessor.process(conflictRequest, "conflicting-entry");

    assertThat(rejected.httpStatus()).isEqualTo(409);
    assertThat(rejected.status().name()).isEqualTo("REJECTED");
    assertThat(duplicate.httpStatus()).isEqualTo(409);
    assertThat(duplicate.duplicate()).isTrue();
    assertThat(sessionRepository.count()).isEqualTo(1);
  }

  @Test
  void shouldRejectReusedProducerKeyWithDifferentPayloadWithoutSideEffects() {
    persistenceService.replaceAll(validator.validate(GarageConfigurationFixtures.valid()));
    webhookProcessor.process(new WebhookRequest(
        "ZUL0001", Instant.parse("2025-01-01T12:00:00Z"), null, null, null,
        WebhookEventType.ENTRY), " shared-request ");

    var conflict = webhookProcessor.process(new WebhookRequest(
        "ZUL0002", Instant.parse("2025-01-01T12:05:00Z"), null, null, null,
        WebhookEventType.ENTRY), "shared-request");

    assertThat(conflict.httpStatus()).isEqualTo(409);
    assertThat(conflict.message())
        .isEqualTo("A chave de idempotência já foi utilizada para outra requisição.");
    assertThat(sessionRepository.count()).isEqualTo(1);
    assertThat(eventRepository.count()).isEqualTo(1);
    assertThat(eventRepository.findAll().getFirst().getIdempotencyKey())
        .isEqualTo("shared-request");
  }

  @Test
  void shouldRejectReusedProducerKeyWithDifferentEventType() {
    persistenceService.replaceAll(validator.validate(GarageConfigurationFixtures.valid()));
    webhookProcessor.process(new WebhookRequest(
        "ZUL0001", Instant.parse("2025-01-01T12:00:00Z"), null, null, null,
        WebhookEventType.ENTRY), "cross-event-request");

    var conflict = webhookProcessor.process(new WebhookRequest(
        "ZUL0001", null, Instant.parse("2025-01-01T13:00:00Z"), null, null,
        WebhookEventType.EXIT), "cross-event-request");

    assertThat(conflict.httpStatus()).isEqualTo(409);
    assertThat(sessionRepository.countByStatus(ParkingSessionStatus.ENTERED)).isEqualTo(1);
    assertThat(eventRepository.count()).isEqualTo(1);
  }

  @Test
  void shouldPersistCanonicalRequestFingerprint() {
    persistenceService.replaceAll(validator.validate(GarageConfigurationFixtures.valid()));
    var request = new WebhookRequest(
        "zul0001", Instant.parse("2025-01-01T12:00:00Z"), null, null, null,
        WebhookEventType.ENTRY);
    webhookProcessor.process(request, "fingerprint-request");

    assertThat(eventRepository.findAll().getFirst().getRequestFingerprint())
        .hasSize(32)
        .isEqualTo(idempotencyKeyService.fingerprint(request, "ZUL0001"));
  }

  @Test
  void shouldUseNoActiveSessionMarkerAndAllowFutureSessionAtSameCoordinates() {
    persistenceService.replaceAll(validator.validate(GarageConfigurationFixtures.valid()));
    var parked = new WebhookRequest(
        "ZUL0001", null, null, new BigDecimal("-23.56168400"),
        new BigDecimal("-46.65598100"), WebhookEventType.PARKED);

    var rejected = webhookProcessor.process(parked, null);
    var repeated = webhookProcessor.process(parked, null);
    createEntry("ZUL0001", "2025-01-01T12:00:00Z", "future-entry");
    var processed = webhookProcessor.process(parked, null);

    assertThat(rejected.httpStatus()).isEqualTo(404);
    assertThat(repeated.duplicate()).isTrue();
    assertThat(processed.httpStatus()).isEqualTo(200);
    assertThat(eventRepository.findAll())
        .extracting(event -> event.getIdempotencyKey())
        .contains(
            "PARKED|ZUL0001|NO_ACTIVE_SESSION|-23.561684|-46.655981",
            "PARKED|ZUL0001|2025-01-01T12:00:00Z|-23.561684|-46.655981");
    assertThat(sessionRepository.countByStatus(ParkingSessionStatus.PARKED)).isEqualTo(1);
  }

  @Test
  void shouldHandleConcurrentRequestsWithTheSameProducerKeyAsOneLogicalRequest()
      throws Exception {
    persistenceService.replaceAll(validator.validate(GarageConfigurationFixtures.valid()));
    var request = new WebhookRequest(
        "ZUL0001", Instant.parse("2025-01-01T12:00:00Z"), null, null, null,
        WebhookEventType.ENTRY);
    var executor = Executors.newFixedThreadPool(2);
    try {
      var first = executor.submit(() -> webhookProcessor.process(request, "same-concurrent-key"));
      var second = executor.submit(() -> webhookProcessor.process(request, "same-concurrent-key"));
      var outcomes = List.of(
          first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));

      assertThat(outcomes).allSatisfy(outcome -> assertThat(outcome.httpStatus()).isEqualTo(200));
      assertThat(outcomes).extracting(outcome -> outcome.duplicate())
          .containsExactlyInAnyOrder(false, true);
      assertThat(sessionRepository.count()).isEqualTo(1);
      assertThat(eventRepository.count()).isEqualTo(1);
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  void shouldSerializeConcurrentEntriesNearGlobalCapacity() throws Exception {
    persistenceService.replaceAll(validator.validate(GarageConfigurationFixtures.valid()));
    var executor = Executors.newFixedThreadPool(2);
    try {
      var first = executor.submit(() -> webhookProcessor.process(new WebhookRequest(
          "ZUL0001", Instant.parse("2025-01-01T12:00:00Z"), null, null, null,
          WebhookEventType.ENTRY), "concurrent-entry-1"));
      var second = executor.submit(() -> webhookProcessor.process(new WebhookRequest(
          "ZUL0002", Instant.parse("2025-01-01T12:00:01Z"), null, null, null,
          WebhookEventType.ENTRY), "concurrent-entry-2"));

      assertThat(List.of(first.get(10, TimeUnit.SECONDS).httpStatus(),
          second.get(10, TimeUnit.SECONDS).httpStatus()))
          .containsExactlyInAnyOrder(200, 409);
      assertThat(sessionRepository.countByStatus(ParkingSessionStatus.ENTERED)).isEqualTo(1);
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  void shouldReserveGlobalCapacityOnEntryAndReleaseItAfterExit() {
    persistenceService.replaceAll(validator.validate(allFreeSnapshot()));

    var firstEntry = webhookProcessor.process(new WebhookRequest(
        "ZUL0001", Instant.parse("2025-01-01T12:00:00Z"), null, null, null,
        WebhookEventType.ENTRY), "capacity-entry-1");
    var secondEntry = webhookProcessor.process(new WebhookRequest(
        "ZUL0002", Instant.parse("2025-01-01T12:00:01Z"), null, null, null,
        WebhookEventType.ENTRY), "capacity-entry-2");

    assertThat(firstEntry.httpStatus()).isEqualTo(200);
    assertThat(secondEntry.httpStatus()).isEqualTo(200);
    assertThat(sessionRepository.countByStatus(ParkingSessionStatus.ENTERED)).isEqualTo(2);
    assertThat(spotRepository.countByOccupiedTrue()).isZero();

    var rejectedEntry = webhookProcessor.process(new WebhookRequest(
        "ZUL0003", Instant.parse("2025-01-01T12:00:02Z"), null, null, null,
        WebhookEventType.ENTRY), "capacity-entry-3-rejected");
    assertThat(rejectedEntry.httpStatus()).isEqualTo(409);
    assertThat(rejectedEntry.message()).isEqualTo("A garagem está cheia.");

    var parked = park("ZUL0001", "-23.56168400", "-46.65598100", "capacity-parked-1");
    var exit = webhookProcessor.process(new WebhookRequest(
        "ZUL0001", null, Instant.parse("2025-01-01T13:00:00Z"), null, null,
        WebhookEventType.EXIT), "capacity-exit-1");

    assertThat(parked.httpStatus()).isEqualTo(200);
    assertThat(exit.httpStatus()).isEqualTo(200);
    assertThat(sessionRepository.findAll())
        .filteredOn(session -> session.getLicensePlate().equals("ZUL0001"))
        .extracting(ParkingSession::getStatus)
        .containsExactly(ParkingSessionStatus.FINISHED);
    assertThat(sessionRepository.findAll())
        .filteredOn(session -> session.getLicensePlate().equals("ZUL0002"))
        .extracting(ParkingSession::getStatus)
        .containsExactly(ParkingSessionStatus.ENTERED);
    assertThat(spotRepository.findByExternalId(1L).orElseThrow().isOccupied()).isFalse();

    var acceptedEntry = webhookProcessor.process(new WebhookRequest(
        "ZUL0003", Instant.parse("2025-01-01T12:00:02Z"), null, null, null,
        WebhookEventType.ENTRY), "capacity-entry-3-accepted");

    assertThat(acceptedEntry.httpStatus()).isEqualTo(200);
    assertThat(sessionRepository.findAll())
        .filteredOn(session -> session.getStatus() != ParkingSessionStatus.FINISHED)
        .extracting(ParkingSession::getLicensePlate)
        .containsExactlyInAnyOrder("ZUL0002", "ZUL0003");
  }

  @Test
  void shouldAggregateFinishedRevenueByExitDayInSaoPaulo() {
    persistenceService.replaceAll(validator.validate(revenueSnapshot()));
    Long sectorA = sectorRepository.findByCode("A").orElseThrow().getId();
    Long sectorB = sectorRepository.findByCode("B").orElseThrow().getId();
    insertSession("ZUL1001", sectorA, "FINISHED", "2025-01-01T03:00:00Z", "10.0000");
    insertSession("ZUL1002", sectorA, "FINISHED", "2025-01-02T02:59:59.999999Z", "79.1000");
    insertSession("ZUL1003", sectorA, "FINISHED", "2025-01-01T02:59:59.999999Z", "100.0000");
    insertSession("ZUL1004", sectorA, "FINISHED", "2025-01-02T03:00:00Z", "100.0000");
    insertSession("ZUL1005", sectorB, "FINISHED", "2025-01-01T12:00:00Z", "100.0000");
    insertSession("ZUL1006", sectorA, "ENTERED", "2025-01-01T12:00:00Z", "100.0000");
    insertSession("ZUL1007", sectorA, "PARKED", "2025-01-01T12:00:00Z", "100.0000");

    var result = revenueService.calculate("2025-01-01", "A");

    assertThat(result.amount()).isEqualByComparingTo("89.10");
    assertThat(result.amount().scale()).isEqualTo(2);
    assertThat(result.currency()).isEqualTo("BRL");
    assertThat(result.timestamp()).isEqualTo(FIXED_INSTANT);
  }

  @Test
  void shouldReturnZeroRevenueWhenThereAreNoFinishedSessionsInTheRequestedDay() {
    persistenceService.replaceAll(validator.validate(revenueSnapshot()));

    var result = revenueService.calculate("2025-01-03", "A");

    assertThat(result.amount()).isEqualByComparingTo("0.00");
    assertThat(result.amount().scale()).isEqualTo(2);
  }

  @Test
  void shouldRoundOnePersistedFractionalCentOnlyWhenPresentingRevenue() {
    persistenceService.replaceAll(validator.validate(revenueSnapshot()));
    Long sectorA = sectorRepository.findByCode("A").orElseThrow().getId();
    insertSession("ZUL2001", sectorA, "FINISHED", "2025-01-01T12:00:00Z", "50.6250");

    var persistedSum = sumRevenueForJanuaryFirst("A");
    var result = revenueService.calculate("2025-01-01", "A");

    assertThat(persistedSum).isEqualByComparingTo("50.6250");
    assertThat(result.amount()).isEqualByComparingTo("50.63");
    assertThat(result.amount().scale()).isEqualTo(2);
  }

  @Test
  void shouldSumPersistedFractionalCentsBeforeRoundingRevenue() {
    persistenceService.replaceAll(validator.validate(revenueSnapshot()));
    Long sectorA = sectorRepository.findByCode("A").orElseThrow().getId();
    insertSession("ZUL2001", sectorA, "FINISHED", "2025-01-01T12:00:00Z", "50.6250");
    insertSession("ZUL2002", sectorA, "FINISHED", "2025-01-01T13:00:00Z", "50.6250");

    var persistedSum = sumRevenueForJanuaryFirst("A");
    var result = revenueService.calculate("2025-01-01", "A");

    assertThat(persistedSum).isEqualByComparingTo("101.2500");
    assertThat(result.amount()).isEqualByComparingTo("101.25");
    assertThat(result.amount()).isNotEqualByComparingTo("101.26");
  }

  @Test
  void shouldLoadInitialSnapshotWhenResetIsFalseAndDatabaseIsEmpty() {
    GarageSimulatorClient client = mock(GarageSimulatorClient.class);
    when(client.fetchConfiguration()).thenReturn(GarageConfigurationFixtures.valid());
    var service = new GarageStartupService(
        client, validator, persistenceService, new GarageStartupProperties(false));

    service.initialize();

    verify(client).fetchConfiguration();
    assertThat(sectorRepository.count()).isEqualTo(1);
    assertThat(spotRepository.count()).isEqualTo(2);
  }

  @Test
  void shouldContinueFromDatabaseWithoutSimulatorWhenResetIsFalse() {
    persistenceService.replaceAll(validator.validate(GarageConfigurationFixtures.valid()));
    GarageSimulatorClient client = mock(GarageSimulatorClient.class);
    var service = new GarageStartupService(
        client, validator, persistenceService, new GarageStartupProperties(false));

    service.initialize();

    verify(client, never()).fetchConfiguration();
    assertThat(spotRepository.findByExternalId(2L).orElseThrow().isOccupied()).isTrue();
  }

  @Test
  void shouldReplaceAllStateFromValidatedSnapshotWhenResetIsTrue() {
    persistenceService.replaceAll(validator.validate(GarageConfigurationFixtures.valid()));
    webhookProcessor.process(new WebhookRequest(
        "ZUL0001", Instant.parse("2025-01-01T12:00:00Z"), null, null, null,
        WebhookEventType.ENTRY), "reset-entry");
    GarageSimulatorClient client = mock(GarageSimulatorClient.class);
    var replacement = configuration(
        List.of(sector("B", 1)),
        List.of(spot(10L, "B", "-22.00000000", "-45.00000000", false)));
    when(client.fetchConfiguration()).thenReturn(replacement);
    var service = new GarageStartupService(
        client, validator, persistenceService, new GarageStartupProperties(true));

    service.initialize();

    assertThat(eventRepository.count()).isZero();
    assertThat(sessionRepository.count()).isZero();
    assertThat(sectorRepository.findByCode("A")).isEmpty();
    assertThat(sectorRepository.findByCode("B")).isPresent();
    assertThat(spotRepository.findByExternalId(10L)).isPresent();
  }

  @Test
  void shouldSerializeConcurrentParkingInDifferentSpotsOfTheSameSector() throws Exception {
    persistenceService.replaceAll(validator.validate(allFreeSnapshot()));
    createEntry("ZUL0001", "2025-01-01T12:00:00Z", "entry-parking-1");
    createEntry("ZUL0002", "2025-01-01T12:00:01Z", "entry-parking-2");
    var executor = Executors.newFixedThreadPool(2);
    try {
      var first = executor.submit(() -> park(
          "ZUL0001", "-23.56168400", "-46.65598100", "parking-1"));
      var second = executor.submit(() -> park(
          "ZUL0002", "-23.56168501", "-46.65598201", "parking-2"));
      assertThat(List.of(first.get(10, TimeUnit.SECONDS).httpStatus(),
          second.get(10, TimeUnit.SECONDS).httpStatus())).containsOnly(200);
      assertThat(sessionRepository.findAll())
          .extracting(ParkingSession::getPriceMultiplier)
          .containsExactlyInAnyOrder(new BigDecimal("0.90"), new BigDecimal("1.10"));
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  void shouldAllowOnlyOneConcurrentParkingOperationForTheSameSpot() throws Exception {
    persistenceService.replaceAll(validator.validate(allFreeSnapshot()));
    createEntry("ZUL0001", "2025-01-01T12:00:00Z", "same-spot-entry-1");
    createEntry("ZUL0002", "2025-01-01T12:00:01Z", "same-spot-entry-2");
    var executor = Executors.newFixedThreadPool(2);
    try {
      var first = executor.submit(() -> park(
          "ZUL0001", "-23.56168400", "-46.65598100", "same-spot-1"));
      var second = executor.submit(() -> park(
          "ZUL0002", "-23.56168400", "-46.65598100", "same-spot-2"));
      assertThat(List.of(first.get(10, TimeUnit.SECONDS).httpStatus(),
          second.get(10, TimeUnit.SECONDS).httpStatus()))
          .containsExactlyInAnyOrder(200, 409);
      assertThat(sessionRepository.countByStatus(ParkingSessionStatus.PARKED)).isEqualTo(1);
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  void shouldSynchronizeIdempotentlyAndPreserveDecimalsExactly() {
    var configuration = validator.validate(GarageConfigurationFixtures.valid());
    persistenceService.synchronize(configuration);
    persistenceService.synchronize(configuration);

    assertThat(sectorRepository.count()).isEqualTo(1);
    assertThat(spotRepository.count()).isEqualTo(2);
    assertThat(sectorRepository.findByCode("A").orElseThrow().getBasePrice())
        .isEqualByComparingTo("40.5000");
    var spot = spotRepository.findByExternalId(1L).orElseThrow();
    assertThat(spot.getLatitude()).isEqualByComparingTo("-23.56168400");
    assertThat(spot.getLongitude()).isEqualByComparingTo("-46.65598100");
  }

  @Test
  void shouldPreserveLocalBusinessHoursAndUtcInstants() {
    Instant beforeSynchronization = Instant.now().minusSeconds(1);
    var snapshot =
        configuration(
            List.of(sector("A", 1, "00:00", "23:59"), sector("B", 1, "08:00", "23:59")),
            List.of(
                spot(1L, "A", "-23.56168400", "-46.65598100", false),
                spot(2L, "B", "-23.56168500", "-46.65598200", false)));

    persistenceService.synchronize(validator.validate(snapshot));
    Instant afterSynchronization = Instant.now().plusSeconds(1);

    var sectorA = sectorRepository.findByCode("A").orElseThrow();
    var sectorB = sectorRepository.findByCode("B").orElseThrow();
    assertThat(sectorA.getOpenHour()).isEqualTo(LocalTime.of(0, 0));
    assertThat(sectorB.getOpenHour()).isEqualTo(LocalTime.of(8, 0));
    assertThat(sectorA.getCloseHour()).isEqualTo(LocalTime.of(23, 59));
    assertThat(sectorB.getCloseHour()).isEqualTo(LocalTime.of(23, 59));

    assertThat(readTime("A", "open_hour")).isEqualTo("00:00");
    assertThat(readTime("B", "open_hour")).isEqualTo("08:00");
    assertThat(readTime("A", "close_hour")).isEqualTo("23:59");

    assertThat(sectorA.getCreatedAt()).isBetween(beforeSynchronization, afterSynchronization);
    assertThat(sectorA.getUpdatedAt()).isBetween(beforeSynchronization, afterSynchronization);
    Long createdAtEpoch =
        jdbcTemplate.queryForObject(
            "SELECT UNIX_TIMESTAMP(created_at) FROM garage_sector WHERE code = 'A'", Long.class);
    assertThat(Instant.ofEpochSecond(createdAtEpoch))
        .isBetween(beforeSynchronization, afterSynchronization);
  }

  @Test
  void shouldRemoveSpotMissingFromLatestSnapshot() {
    persistenceService.synchronize(validator.validate(GarageConfigurationFixtures.valid()));
    var latest =
        configuration(
            List.of(sector("A", 1)), List.of(spot(1L, "A", "-23.56168400", "-46.65598100", false)));

    persistenceService.synchronize(validator.validate(latest));

    assertThat(spotRepository.findByExternalId(1L)).isPresent();
    assertThat(spotRepository.findByExternalId(2L)).isEmpty();
    assertThat(spotRepository.count()).isEqualTo(1);
  }

  @Test
  void shouldRemoveSectorMissingFromLatestSnapshot() {
    var initial =
        configuration(
            List.of(sector("A", 1), sector("B", 1)),
            List.of(
                spot(1L, "A", "-23.56168400", "-46.65598100", false),
                spot(2L, "B", "-23.56168500", "-46.65598200", false)));
    persistenceService.synchronize(validator.validate(initial));
    var latest =
        configuration(
            List.of(sector("A", 1)), List.of(spot(1L, "A", "-23.56168400", "-46.65598100", false)));

    persistenceService.synchronize(validator.validate(latest));

    assertThat(sectorRepository.findByCode("A")).isPresent();
    assertThat(sectorRepository.findByCode("B")).isEmpty();
    assertThat(sectorRepository.count()).isEqualTo(1);
  }

  @Test
  void shouldOverwriteOccupiedStateFromLatestSnapshot() {
    persistenceService.synchronize(validator.validate(GarageConfigurationFixtures.valid()));
    var latest =
        configuration(
            List.of(sector("A", 2)),
            List.of(
                spot(1L, "A", "-23.56168400", "-46.65598100", true),
                spot(2L, "A", "-23.56168501", "-46.65598201", false)));

    persistenceService.synchronize(validator.validate(latest));

    assertThat(spotRepository.findByExternalId(1L).orElseThrow().isOccupied()).isTrue();
    assertThat(spotRepository.findByExternalId(2L).orElseThrow().isOccupied()).isFalse();
  }

  @Test
  void shouldRollBackReconciliationWhenObsoleteSpotHasDatabaseReferences() {
    persistenceService.synchronize(validator.validate(GarageConfigurationFixtures.valid()));
    jdbcTemplate.execute(
        """
        CREATE TABLE spot_reference (
            id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
            spot_id BIGINT NOT NULL,
            CONSTRAINT fk_spot_reference_spot FOREIGN KEY (spot_id) REFERENCES parking_spot (id)
        )
        """);
    Long referencedSpotId = spotRepository.findByExternalId(1L).orElseThrow().getId();
    jdbcTemplate.update("INSERT INTO spot_reference (spot_id) VALUES (?)", referencedSpotId);
    var latest =
        configuration(
            List.of(sector("A", 1)), List.of(spot(2L, "A", "-23.56168501", "-46.65598201", false)));

    assertThatThrownBy(() -> persistenceService.synchronize(validator.validate(latest)))
        .isInstanceOf(GarageReconciliationException.class)
        .hasMessageContaining("nenhuma alteração foi aplicada")
        .hasCauseInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    assertThat(spotRepository.count()).isEqualTo(2);
    assertThat(sectorRepository.findByCode("A").orElseThrow().getMaxCapacity()).isEqualTo(2);
  }

  @Test
  void shouldNotPersistAnythingWhenCompleteConfigurationIsInvalid() {
    var valid = GarageConfigurationFixtures.valid();
    var invalid =
        new GarageConfigurationResponse(valid.garage(), List.of(valid.spots().getFirst()));
    assertThatThrownBy(() -> persistenceService.synchronize(validator.validate(invalid)))
        .isInstanceOf(InvalidGarageConfigurationException.class);
    assertThat(sectorRepository.count()).isZero();
    assertThat(spotRepository.count()).isZero();
  }

  @Test
  void shouldRollBackAllWritesWhenUnexpectedDatabaseConstraintViolationOccurs() {
    var valid = GarageConfigurationFixtures.valid();
    var duplicateDatabaseCoordinates =
        new ValidatedGarageConfiguration(
            valid.garage().stream()
                .map(
                    sector ->
                        new ValidatedGarageConfiguration.Sector(
                            sector.sector(),
                            sector.basePrice(),
                            sector.maxCapacity(),
                            LocalTime.parse(sector.openHour()),
                            LocalTime.parse(sector.closeHour()),
                            sector.durationLimitMinutes()))
                .toList(),
            List.of(
                new ValidatedGarageConfiguration.Spot(
                    1L, "A", BigDecimal.ONE, BigDecimal.TEN, false),
                new ValidatedGarageConfiguration.Spot(
                    2L, "A", BigDecimal.ONE, BigDecimal.TEN, false)));

    assertThatThrownBy(() -> persistenceService.synchronize(duplicateDatabaseCoordinates))
        .isInstanceOf(RuntimeException.class);
    assertThat(sectorRepository.count()).isZero();
    assertThat(spotRepository.count()).isZero();
  }

  private GarageConfigurationResponse configuration(
      List<GarageConfigurationResponse.SectorResponse> sectors,
      List<GarageConfigurationResponse.SpotResponse> spots) {
    return new GarageConfigurationResponse(sectors, spots);
  }

  private GarageConfigurationResponse allFreeSnapshot() {
    return configuration(
        List.of(sector("A", 2)),
        List.of(
            spot(1L, "A", "-23.56168400", "-46.65598100", false),
            spot(2L, "A", "-23.56168501", "-46.65598201", false)));
  }

  private GarageConfigurationResponse revenueSnapshot() {
    return configuration(
        List.of(sector("A", 1), sector("B", 1)),
        List.of(
            spot(1L, "A", "-23.56168400", "-46.65598100", false),
            spot(2L, "B", "-23.56168501", "-46.65598201", false)));
  }

  private void insertSession(
      String plate, Long sectorId, String status, String exitTime, String amount) {
    jdbcTemplate.update(
        "INSERT INTO parking_session"
            + " (license_plate, sector_id, entry_time, exit_time, amount, status,"
            + " created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, NOW(6), NOW(6))",
        plate,
        sectorId,
        LocalDateTime.ofInstant(Instant.parse("2024-12-31T00:00:00Z"), ZoneOffset.UTC),
        LocalDateTime.ofInstant(Instant.parse(exitTime), ZoneOffset.UTC),
        new BigDecimal(amount),
        status);
  }

  private BigDecimal sumRevenueForJanuaryFirst(String sectorCode) {
    return sessionRepository.sumAmountBySectorAndStatusAndExitTime(
        sectorCode,
        ParkingSessionStatus.FINISHED,
        Instant.parse("2025-01-01T03:00:00Z"),
        Instant.parse("2025-01-02T03:00:00Z"));
  }

  private void createEntry(String plate, String entryTime, String key) {
    webhookProcessor.process(new WebhookRequest(
        plate, Instant.parse(entryTime), null, null, null, WebhookEventType.ENTRY), key);
  }

  private com.tonyleiva.parkinggarage.application.webhook.WebhookOutcome park(
      String plate, String latitude, String longitude, String key) {
    return webhookProcessor.process(new WebhookRequest(
        plate, null, null, new BigDecimal(latitude), new BigDecimal(longitude),
        WebhookEventType.PARKED), key);
  }

  private GarageConfigurationResponse.SectorResponse sector(String code, int capacity) {
    return sector(code, capacity, "00:00", "23:59");
  }

  private GarageConfigurationResponse.SectorResponse sector(
      String code, int capacity, String openHour, String closeHour) {
    return new GarageConfigurationResponse.SectorResponse(
        code, new BigDecimal("40.5000"), capacity, openHour, closeHour, 1440);
  }

  private String readTime(String sectorCode, String column) {
    return jdbcTemplate.queryForObject(
        "SELECT TIME_FORMAT(" + column + ", '%H:%i') FROM garage_sector WHERE code = ?",
        String.class,
        sectorCode);
  }

  private GarageConfigurationResponse.SpotResponse spot(
      long id, String sector, String latitude, String longitude, boolean occupied) {
    return new GarageConfigurationResponse.SpotResponse(
        id, sector, new BigDecimal(latitude), new BigDecimal(longitude), occupied);
  }
}
