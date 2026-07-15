package com.tonyleiva.parkinggarage.application.garage;

import com.tonyleiva.parkinggarage.infrastructure.client.GarageConfigurationResponse;
import java.math.BigDecimal;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class GarageConfigurationValidator {
  private static final DateTimeFormatter TIME_FORMAT =
      DateTimeFormatter.ofPattern("HH:mm").withResolverStyle(ResolverStyle.STRICT);

  public ValidatedGarageConfiguration validate(GarageConfigurationResponse response) {
    if (response == null || response.garage() == null || response.spots() == null) {
      throw invalid("as listas de setores e vagas são obrigatórias.");
    }
    if (response.garage().isEmpty()) {
      throw invalid("a lista de setores não pode estar vazia.");
    }
    List<ValidatedGarageConfiguration.Sector> sectors = validateSectors(response.garage());
    if (response.spots().isEmpty()) {
      throw invalid("a lista de vagas não pode estar vazia.");
    }
    Set<String> sectorCodes =
        sectors.stream().map(ValidatedGarageConfiguration.Sector::code).collect(Collectors.toSet());
    List<ValidatedGarageConfiguration.Spot> spots = validateSpots(response.spots(), sectorCodes);
    validateCapacity(sectors, spots);
    return new ValidatedGarageConfiguration(List.copyOf(sectors), List.copyOf(spots));
  }

  private List<ValidatedGarageConfiguration.Sector> validateSectors(
      List<GarageConfigurationResponse.SectorResponse> source) {
    Set<String> codes = new HashSet<>();
    List<ValidatedGarageConfiguration.Sector> result = new ArrayList<>();
    for (var sector : source) {
      if (sector == null || sector.sector() == null || sector.sector().isBlank())
        throw invalid("foi recebido um setor sem código.");
      String code = sector.sector().trim();
      if (!codes.add(code)) throw invalid("o setor " + code + " foi informado mais de uma vez.");
      if (sector.basePrice() == null)
        throw invalid("o setor " + code + " não informou o preço base.");
      if (sector.basePrice().signum() < 0)
        throw invalid("o setor " + code + " possui preço base negativo.");
      if (sector.maxCapacity() == null || sector.maxCapacity() <= 0)
        throw invalid("o setor " + code + " deve possuir capacidade máxima maior que zero.");
      if (sector.durationLimitMinutes() == null || sector.durationLimitMinutes() <= 0)
        throw invalid("o setor " + code + " deve possuir limite de duração maior que zero.");
      LocalTime open = parseTime(sector.openHour(), code, "abertura");
      LocalTime close = parseTime(sector.closeHour(), code, "fechamento");
      result.add(
          new ValidatedGarageConfiguration.Sector(
              code,
              sector.basePrice(),
              sector.maxCapacity(),
              open,
              close,
              sector.durationLimitMinutes()));
    }
    return result;
  }

  private List<ValidatedGarageConfiguration.Spot> validateSpots(
      List<GarageConfigurationResponse.SpotResponse> source, Set<String> sectorCodes) {
    Set<Long> ids = new HashSet<>();
    Set<Coordinates> coordinates = new HashSet<>();
    List<ValidatedGarageConfiguration.Spot> result = new ArrayList<>();
    for (var spot : source) {
      if (spot == null || spot.id() == null) {
        throw invalid("foi recebida uma vaga sem identificador externo.");
      }
      if (spot.id() <= 0) {
        throw invalid(
            "a vaga deve possuir identificador externo maior que zero: " + spot.id() + ".");
      }
      if (!ids.add(spot.id()))
        throw invalid(
            "a vaga com identificador externo " + spot.id() + " foi informada mais de uma vez.");
      if (spot.sector() == null || !sectorCodes.contains(spot.sector()))
        throw invalid(
            "a vaga " + spot.id() + " referencia o setor inexistente " + spot.sector() + ".");
      if (spot.lat() == null || spot.lng() == null)
        throw invalid("a vaga " + spot.id() + " não possui coordenadas completas.");
      Coordinates coordinate = new Coordinates(normalize(spot.lat()), normalize(spot.lng()));
      if (!coordinates.add(coordinate))
        throw invalid(
            "as coordenadas "
                + spot.lat()
                + ", "
                + spot.lng()
                + " foram informadas para mais de uma vaga.");
      if (spot.occupied() == null)
        throw invalid("a vaga " + spot.id() + " não informou seu estado de ocupação.");
      result.add(
          new ValidatedGarageConfiguration.Spot(
              spot.id(), spot.sector(), spot.lat(), spot.lng(), spot.occupied()));
    }
    return result;
  }

  private void validateCapacity(
      List<ValidatedGarageConfiguration.Sector> sectors,
      List<ValidatedGarageConfiguration.Spot> spots) {
    Map<String, Long> totals =
        spots.stream()
            .collect(
                Collectors.groupingBy(
                    ValidatedGarageConfiguration.Spot::sectorCode, Collectors.counting()));
    for (var sector : sectors) {
      long received = totals.getOrDefault(sector.code(), 0L);
      if (received != sector.maxCapacity()) {
        throw invalid(
            "o setor "
                + sector.code()
                + " declara capacidade máxima de "
                + sector.maxCapacity()
                + " vagas, mas foram recebidas "
                + received
                + " vagas.");
      }
    }
  }

  private LocalTime parseTime(String value, String sector, String field) {
    try {
      if (value == null) throw new DateTimeParseException("valor nulo", "", 0);
      return LocalTime.parse(value, TIME_FORMAT);
    } catch (DateTimeParseException exception) {
      throw new InvalidGarageConfigurationException(
          "A configuração da garagem é inconsistente: o horário de "
              + field
              + " do setor "
              + sector
              + " é inválido: "
              + value
              + ".",
          exception);
    }
  }

  private BigDecimal normalize(BigDecimal value) {
    return value.stripTrailingZeros();
  }

  private InvalidGarageConfigurationException invalid(String detail) {
    return new InvalidGarageConfigurationException(
        "A configuração da garagem é inconsistente: " + detail);
  }

  private record Coordinates(BigDecimal latitude, BigDecimal longitude) {}
}
