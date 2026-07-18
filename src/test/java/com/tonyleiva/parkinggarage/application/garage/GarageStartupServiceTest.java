package com.tonyleiva.parkinggarage.application.garage;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tonyleiva.parkinggarage.infrastructure.client.GarageSimulatorClient;
import com.tonyleiva.parkinggarage.infrastructure.config.GarageStartupProperties;
import java.util.List;
import org.junit.jupiter.api.Test;

class GarageStartupServiceTest {
  @Test
  void shouldValidateSnapshotBeforeReplacingStateOnReset() {
    GarageSimulatorClient client = mock(GarageSimulatorClient.class);
    GarageConfigurationValidator validator = mock(GarageConfigurationValidator.class);
    GaragePersistenceService persistence = mock(GaragePersistenceService.class);
    var response = GarageConfigurationFixtures.valid();
    var validated = new ValidatedGarageConfiguration(List.of(), List.of());
    when(client.fetchConfiguration()).thenReturn(response);
    when(validator.validate(response)).thenReturn(validated);
    when(persistence.replaceAll(validated)).thenReturn(summary());

    new GarageStartupService(client, validator, persistence, new GarageStartupProperties(true))
        .initialize();

    var order = inOrder(client, validator, persistence);
    order.verify(client).fetchConfiguration();
    order.verify(validator).validate(response);
    order.verify(persistence).replaceAll(validated);
  }

  @Test
  void shouldLoadSimulatorWhenDatabaseIsEmpty() {
    GarageSimulatorClient client = mock(GarageSimulatorClient.class);
    GarageConfigurationValidator validator = mock(GarageConfigurationValidator.class);
    GaragePersistenceService persistence = mock(GaragePersistenceService.class);
    var response = GarageConfigurationFixtures.valid();
    var validated = new ValidatedGarageConfiguration(List.of(), List.of());
    when(persistence.isEmpty()).thenReturn(true);
    when(client.fetchConfiguration()).thenReturn(response);
    when(validator.validate(response)).thenReturn(validated);
    when(persistence.replaceAll(validated)).thenReturn(summary());

    new GarageStartupService(client, validator, persistence, new GarageStartupProperties(false))
        .initialize();

    verify(client).fetchConfiguration();
    verify(persistence).replaceAll(validated);
  }

  @Test
  void shouldContinueFromDatabaseWithoutCallingSimulator() {
    GarageSimulatorClient client = mock(GarageSimulatorClient.class);
    GarageConfigurationValidator validator = mock(GarageConfigurationValidator.class);
    GaragePersistenceService persistence = mock(GaragePersistenceService.class);
    when(persistence.isEmpty()).thenReturn(false);
    when(persistence.summarize()).thenReturn(summary());

    new GarageStartupService(client, validator, persistence, new GarageStartupProperties(false))
        .initialize();

    verify(client, never()).fetchConfiguration();
    verify(persistence).validateStoredConfiguration();
    verify(persistence).summarize();
  }

  private GarageStateSummary summary() {
    return new GarageStateSummary(1, 1, 0, List.of());
  }
}
