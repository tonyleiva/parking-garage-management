package com.tonyleiva.parkinggarage.application.garage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tonyleiva.parkinggarage.infrastructure.client.GarageConfigurationResponse;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class GarageConfigurationValidatorTest {
  private final GarageConfigurationValidator validator = new GarageConfigurationValidator();

  @Test
  void shouldValidateACompleteConfiguration() {
    var result = validator.validate(GarageConfigurationFixtures.valid());
    assertThat(result.sectors()).hasSize(1);
    assertThat(result.spots()).hasSize(2);
  }

  @Test
  void shouldRejectEmptySectorList() {
    assertInvalid(
        new GarageConfigurationResponse(List.of(), GarageConfigurationFixtures.valid().spots()),
        "a lista de setores não pode estar vazia");
  }

  @Test
  void shouldRejectEmptySpotList() {
    assertInvalid(
        new GarageConfigurationResponse(GarageConfigurationFixtures.valid().garage(), List.of()),
        "a lista de vagas não pode estar vazia");
  }

  @Test
  void shouldRejectNonPositiveSpotExternalId() {
    var valid = GarageConfigurationFixtures.valid();
    var spots = new ArrayList<>(valid.spots());
    spots.set(
        0,
        new GarageConfigurationResponse.SpotResponse(
            0L, "A", BigDecimal.ONE, BigDecimal.TEN, false));

    assertInvalid(
        new GarageConfigurationResponse(valid.garage(), spots),
        "identificador externo maior que zero: 0");
  }

  @Test
  void shouldRejectDuplicateSector() {
    var valid = GarageConfigurationFixtures.valid();
    var response =
        new GarageConfigurationResponse(
            List.of(valid.garage().getFirst(), valid.garage().getFirst()), valid.spots());
    assertInvalid(response, "setor A foi informado mais de uma vez");
  }

  @Test
  void shouldRejectDuplicateSpotExternalId() {
    var valid = GarageConfigurationFixtures.valid();
    var spots =
        List.of(
            valid.spots().getFirst(),
            new GarageConfigurationResponse.SpotResponse(
                1L, "A", new BigDecimal("1"), new BigDecimal("2"), false));
    assertInvalid(
        new GarageConfigurationResponse(valid.garage(), spots),
        "identificador externo 1 foi informada mais de uma vez");
  }

  @Test
  void shouldRejectDuplicateCoordinatesEvenWithDifferentScale() {
    var valid = GarageConfigurationFixtures.valid();
    var first = valid.spots().getFirst();
    var spots =
        List.of(
            first,
            new GarageConfigurationResponse.SpotResponse(
                2L, "A", new BigDecimal("-23.5616840"), new BigDecimal("-46.655981000"), false));
    assertInvalid(
        new GarageConfigurationResponse(valid.garage(), spots),
        "foram informadas para mais de uma vaga");
  }

  @Test
  void shouldRejectSpotWithUnknownSector() {
    var valid = GarageConfigurationFixtures.valid();
    var spots = new ArrayList<>(valid.spots());
    spots.set(
        0,
        new GarageConfigurationResponse.SpotResponse(
            1L, "B", BigDecimal.ONE, BigDecimal.TEN, false));
    assertInvalid(
        new GarageConfigurationResponse(valid.garage(), spots), "referencia o setor inexistente B");
  }

  @Test
  void shouldRejectCapacityDifferentFromSpotCount() {
    var valid = GarageConfigurationFixtures.valid();
    assertInvalid(
        new GarageConfigurationResponse(valid.garage(), List.of(valid.spots().getFirst())),
        "declara capacidade máxima de 2 vagas, mas foram recebidas 1 vagas");
  }

  @Test
  void shouldRejectNegativeBasePrice() {
    var sector =
        new GarageConfigurationResponse.SectorResponse(
            "A", new BigDecimal("-0.01"), 2, "00:00", "23:59", 60);
    assertInvalid(
        new GarageConfigurationResponse(
            List.of(sector), GarageConfigurationFixtures.valid().spots()),
        "possui preço base negativo");
  }

  @Test
  void shouldRejectNonPositiveCapacity() {
    var sector =
        new GarageConfigurationResponse.SectorResponse(
            "A", BigDecimal.ONE, 0, "00:00", "23:59", 60);
    assertInvalid(
        new GarageConfigurationResponse(List.of(sector), List.of()),
        "capacidade máxima maior que zero");
  }

  @Test
  void shouldRejectNonPositiveDuration() {
    var sector =
        new GarageConfigurationResponse.SectorResponse("A", BigDecimal.ONE, 1, "00:00", "23:59", 0);
    assertInvalid(
        new GarageConfigurationResponse(List.of(sector), List.of()),
        "limite de duração maior que zero");
  }

  @Test
  void shouldRejectInvalidTimeAndPreserveCause() {
    var sector =
        new GarageConfigurationResponse.SectorResponse(
            "A", BigDecimal.ONE, 1, "25:00", "23:59", 60);
    assertThatThrownBy(
            () -> validator.validate(new GarageConfigurationResponse(List.of(sector), List.of())))
        .isInstanceOf(InvalidGarageConfigurationException.class)
        .hasMessageContaining("horário de abertura do setor A é inválido")
        .hasCauseInstanceOf(java.time.format.DateTimeParseException.class);
  }

  private void assertInvalid(GarageConfigurationResponse response, String message) {
    assertThatThrownBy(() -> validator.validate(response))
        .isInstanceOf(InvalidGarageConfigurationException.class)
        .hasMessageContaining(message);
  }
}
