package com.tonyleiva.parkinggarage.application.garage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.tonyleiva.parkinggarage.infrastructure.client.GarageConfigurationResponse;
import com.tonyleiva.parkinggarage.infrastructure.client.GarageSimulatorClient;
import com.tonyleiva.parkinggarage.infrastructure.persistence.GarageSectorRepository;
import com.tonyleiva.parkinggarage.infrastructure.persistence.ParkingSpotRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

@Testcontainers
@SpringBootTest(properties = "garage.synchronization.enabled=false")
class GarageSynchronizationIntegrationTest {

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

  @Autowired JdbcTemplate jdbcTemplate;

  @BeforeEach
  void cleanDatabase() {
    jdbcTemplate.execute("DROP TABLE IF EXISTS spot_reference");
    spotRepository.deleteAll();
    sectorRepository.deleteAll();
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
        .hasMessageContaining("registros obsoletos do tipo vagas")
        .hasCauseInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    assertThat(spotRepository.count()).isEqualTo(2);
    assertThat(sectorRepository.findByCode("A").orElseThrow().getMaxCapacity()).isEqualTo(2);
  }

  @Test
  void shouldNotPersistAnythingWhenCompleteConfigurationIsInvalid() {
    var valid = GarageConfigurationFixtures.valid();
    var invalid =
        new GarageConfigurationResponse(valid.garage(), List.of(valid.spots().getFirst()));
    GarageSimulatorClient client = mock(GarageSimulatorClient.class);
    when(client.fetchConfiguration()).thenReturn(invalid);
    GarageSynchronizationService service =
        new GarageSynchronizationService(client, validator, persistenceService);

    assertThatThrownBy(service::synchronize)
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
