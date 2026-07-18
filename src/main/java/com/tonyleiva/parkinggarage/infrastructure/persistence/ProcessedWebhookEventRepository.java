package com.tonyleiva.parkinggarage.infrastructure.persistence;

import com.tonyleiva.parkinggarage.domain.webhook.ProcessedWebhookEvent;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedWebhookEventRepository
    extends JpaRepository<ProcessedWebhookEvent, Long> {
  Optional<ProcessedWebhookEvent> findByIdempotencyHash(byte[] idempotencyHash);
}
