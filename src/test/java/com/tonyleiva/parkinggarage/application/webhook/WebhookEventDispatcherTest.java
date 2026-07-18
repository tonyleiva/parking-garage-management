package com.tonyleiva.parkinggarage.application.webhook;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.tonyleiva.parkinggarage.domain.webhook.WebhookEventType;
import java.util.List;
import org.junit.jupiter.api.Test;

class WebhookEventDispatcherTest {
  @Test
  void shouldRejectDuplicateHandlersAtStartup() {
    WebhookEventHandler first = mock(WebhookEventHandler.class);
    WebhookEventHandler second = mock(WebhookEventHandler.class);
    when(first.supportedType()).thenReturn(WebhookEventType.ENTRY);
    when(second.supportedType()).thenReturn(WebhookEventType.ENTRY);

    assertThatThrownBy(() -> new WebhookEventDispatcher(List.of(first, second)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("mais de um handler");
  }
}
