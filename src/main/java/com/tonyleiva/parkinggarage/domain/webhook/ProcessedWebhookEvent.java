package com.tonyleiva.parkinggarage.domain.webhook;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "processed_webhook_event")
public class ProcessedWebhookEvent {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "idempotency_key", nullable = false, length = 512)
  private String idempotencyKey;

  @JdbcTypeCode(SqlTypes.BINARY)
  @Column(name = "idempotency_hash", nullable = false, length = 32)
  private byte[] idempotencyHash;

  @JdbcTypeCode(SqlTypes.BINARY)
  @Column(name = "request_fingerprint", nullable = false, length = 32)
  private byte[] requestFingerprint;

  @Enumerated(EnumType.STRING)
  @Column(name = "event_type", nullable = false, length = 20)
  private WebhookEventType eventType;

  @Column(name = "license_plate", nullable = false, length = 20)
  private String licensePlate;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private WebhookProcessingResult result;

  @Column(name = "http_status", nullable = false)
  private int httpStatus;

  @Column(name = "operational_message", nullable = false, length = 500)
  private String operationalMessage;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected ProcessedWebhookEvent() {}

  public ProcessedWebhookEvent(
      String idempotencyKey,
      byte[] idempotencyHash,
      byte[] requestFingerprint,
      WebhookEventType eventType,
      String licensePlate,
      WebhookProcessingResult result,
      int httpStatus,
      String operationalMessage) {
    this.idempotencyKey = idempotencyKey;
    this.idempotencyHash = idempotencyHash.clone();
    this.requestFingerprint = requestFingerprint.clone();
    this.eventType = eventType;
    this.licensePlate = licensePlate;
    this.result = result;
    this.httpStatus = httpStatus;
    this.operationalMessage = operationalMessage;
  }

  @PrePersist
  void onCreate() { createdAt = Instant.now(); }

  public Long getId() { return id; }
  public String getIdempotencyKey() { return idempotencyKey; }
  public byte[] getIdempotencyHash() { return idempotencyHash.clone(); }
  public byte[] getRequestFingerprint() { return requestFingerprint.clone(); }
  public WebhookEventType getEventType() { return eventType; }
  public String getLicensePlate() { return licensePlate; }
  public WebhookProcessingResult getResult() { return result; }
  public int getHttpStatus() { return httpStatus; }
  public String getOperationalMessage() { return operationalMessage; }
  public Instant getCreatedAt() { return createdAt; }
}
