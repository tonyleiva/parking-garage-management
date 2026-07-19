package com.tonyleiva.parkinggarage.presentation.revenue;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tonyleiva.parkinggarage.application.revenue.RevenueService;
import com.tonyleiva.parkinggarage.domain.sector.GarageSector;
import com.tonyleiva.parkinggarage.domain.session.ParkingSessionStatus;
import com.tonyleiva.parkinggarage.infrastructure.persistence.GarageSectorRepository;
import com.tonyleiva.parkinggarage.infrastructure.persistence.ParkingSessionRepository;
import com.tonyleiva.parkinggarage.presentation.error.GlobalExceptionHandler;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class RevenueControllerTest {
  private static final Instant QUERY_TIME = Instant.parse("2025-01-01T12:00:00Z");
  private GarageSectorRepository sectorRepository;
  private ParkingSessionRepository sessionRepository;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    sectorRepository = mock(GarageSectorRepository.class);
    sessionRepository = mock(ParkingSessionRepository.class);
    Clock clock = Clock.fixed(QUERY_TIME, ZoneOffset.UTC);
    var service = new RevenueService(sectorRepository, sessionRepository, clock);
    mockMvc = MockMvcBuilders.standaloneSetup(new RevenueController(service))
        .setControllerAdvice(new GlobalExceptionHandler())
        .build();
  }

  @Test
  void shouldReturnTheExactRevenueContractWithNumericAmount() throws Exception {
    when(sectorRepository.findByCode("A")).thenReturn(Optional.of(mock(GarageSector.class)));
    when(sessionRepository.sumAmountBySectorAndStatusAndExitTime(
        org.mockito.ArgumentMatchers.eq("A"),
        org.mockito.ArgumentMatchers.eq(ParkingSessionStatus.FINISHED),
        org.mockito.ArgumentMatchers.any(),
        org.mockito.ArgumentMatchers.any())).thenReturn(new BigDecimal("50.6250"));

    mockMvc.perform(get("/revenue")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"date":"2025-01-01","sector":"A"}
                """))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.*", hasSize(3)))
        .andExpect(jsonPath("$.amount").isNumber())
        .andExpect(jsonPath("$.amount").value(50.63))
        .andExpect(jsonPath("$.currency").value("BRL"))
        .andExpect(jsonPath("$.timestamp").value("2025-01-01T12:00:00.000Z"))
        .andExpect(jsonPath("$.duplicate").doesNotExist());
  }

  @Test
  void shouldReturnOkAndZeroWithTwoDecimalPlacesWhenThereIsNoRevenue() throws Exception {
    when(sectorRepository.findByCode("A")).thenReturn(Optional.of(mock(GarageSector.class)));
    when(sessionRepository.sumAmountBySectorAndStatusAndExitTime(
        org.mockito.ArgumentMatchers.eq("A"),
        org.mockito.ArgumentMatchers.eq(ParkingSessionStatus.FINISHED),
        org.mockito.ArgumentMatchers.any(),
        org.mockito.ArgumentMatchers.any())).thenReturn(BigDecimal.ZERO);

    mockMvc.perform(get("/revenue")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"date":"2025-01-01","sector":"A"}
                """))
        .andExpect(status().isOk())
        .andExpect(content().json("""
            {
              "amount": 0.00,
              "currency": "BRL",
              "timestamp": "2025-01-01T12:00:00.000Z"
            }
            """, JsonCompareMode.STRICT));
  }

  @Test
  void shouldReturnNotFoundForUnknownSectorWithoutDuplicate() throws Exception {
    when(sectorRepository.findByCode("UNKNOWN")).thenReturn(Optional.empty());

    mockMvc.perform(get("/revenue")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"date":"2025-01-01","sector":"UNKNOWN"}
                """))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.*", hasSize(2)))
        .andExpect(jsonPath("$.status").value("NOT_FOUND"))
        .andExpect(jsonPath("$.message").value("O setor informado não foi encontrado: UNKNOWN."))
        .andExpect(jsonPath("$.duplicate").doesNotExist());
  }

  @ParameterizedTest
  @ValueSource(strings = {
      "{\"sector\":\"A\"}",
      "{\"date\":null,\"sector\":\"A\"}",
      "{\"date\":\"\",\"sector\":\"A\"}",
      "{\"date\":\"01-01-2025\",\"sector\":\"A\"}",
      "{\"date\":\"2026-02-30\",\"sector\":\"A\"}"
  })
  void shouldRejectMissingNullEmptyMalformedOrImpossibleDate(String body) throws Exception {
    mockMvc.perform(get("/revenue").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value(
            "A data deve estar no formato yyyy-MM-dd, por exemplo: 2026-06-19."))
        .andExpect(jsonPath("$.duplicate").doesNotExist());
  }

  @ParameterizedTest
  @ValueSource(strings = {
      "{\"date\":\"2025-01-01\"}",
      "{\"date\":\"2025-01-01\",\"sector\":null}",
      "{\"date\":\"2025-01-01\",\"sector\":\"\"}"
  })
  void shouldRejectMissingNullOrEmptySector(String body) throws Exception {
    mockMvc.perform(get("/revenue").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("O setor é obrigatório."))
        .andExpect(jsonPath("$.duplicate").doesNotExist());
  }
}
