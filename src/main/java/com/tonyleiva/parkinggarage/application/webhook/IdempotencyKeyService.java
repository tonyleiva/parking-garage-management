package com.tonyleiva.parkinggarage.application.webhook;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import org.springframework.stereotype.Component;

@Component
public class IdempotencyKeyService {
  private static final int MAX_LENGTH = 512;

  public IdempotencyKey fromHeader(String header) {
    if (header == null) return null;
    String normalized = header.trim();
    if (normalized.isEmpty()) {
      throw new InvalidWebhookPayloadException("O header Idempotency-Key não pode estar vazio.");
    }
    return create(normalized);
  }

  public IdempotencyKey entry(String plate, Instant entryTime) {
    return create("ENTRY|" + plate + "|" + entryTime);
  }

  public IdempotencyKey parked(
      String plate, Instant sessionEntryTime, BigDecimal latitude, BigDecimal longitude) {
    return create(
        "PARKED|" + plate + "|" + sessionEntryTime + "|" + decimal(latitude) + "|"
            + decimal(longitude));
  }

  public IdempotencyKey parkedWithoutActiveSession(
      String plate, BigDecimal latitude, BigDecimal longitude) {
    return create(
        "PARKED|" + plate + "|NO_ACTIVE_SESSION|" + decimal(latitude) + "|"
            + decimal(longitude));
  }

  public IdempotencyKey exit(String plate, Instant exitTime) {
    return create("EXIT|" + plate + "|" + exitTime);
  }

  public String normalizePlate(String plate) {
    if (plate == null || plate.trim().isEmpty()) {
      throw new InvalidWebhookPayloadException("A placa é obrigatória.");
    }
    String normalized = plate.trim().toUpperCase(java.util.Locale.ROOT);
    if (normalized.length() > 20) {
      throw new InvalidWebhookPayloadException("A placa deve possuir no máximo 20 caracteres.");
    }
    return normalized;
  }

  public byte[] fingerprint(WebhookRequest request, String normalizedPlate) {
    String canonical = switch (request.eventType()) {
      case ENTRY -> "ENTRY|" + normalizedPlate + "|" + request.entryTime();
      case PARKED -> "PARKED|" + normalizedPlate + "|" + decimal(request.latitude()) + "|"
          + decimal(request.longitude());
      case EXIT -> "EXIT|" + normalizedPlate + "|" + request.exitTime();
    };
    return sha256(canonical);
  }

  private IdempotencyKey create(String logicalKey) {
    if (logicalKey.length() > MAX_LENGTH) {
      throw new InvalidWebhookPayloadException(
          "A chave de idempotência deve possuir no máximo 512 caracteres.");
    }
    return new IdempotencyKey(logicalKey, sha256(logicalKey));
  }

  private byte[] sha256(String value) {
    try {
      return MessageDigest.getInstance("SHA-256")
          .digest(value.getBytes(StandardCharsets.UTF_8));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("Não foi possível gerar o hash de idempotência.", exception);
    }
  }

  private String decimal(BigDecimal value) {
    return value.stripTrailingZeros().toPlainString();
  }
}
