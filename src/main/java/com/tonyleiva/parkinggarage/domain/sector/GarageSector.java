package com.tonyleiva.parkinggarage.domain.sector;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;

@Entity
@Table(name = "garage_sector")
public class GarageSector {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, unique = true, length = 50)
  private String code;

  @Column(name = "base_price", nullable = false, precision = 19, scale = 4)
  private BigDecimal basePrice;

  @Column(name = "max_capacity", nullable = false)
  private int maxCapacity;

  @Column(name = "open_hour", nullable = false)
  private LocalTime openHour;

  @Column(name = "close_hour", nullable = false)
  private LocalTime closeHour;

  @Column(name = "duration_limit_minutes", nullable = false)
  private int durationLimitMinutes;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected GarageSector() {}

  public GarageSector(
      String code,
      BigDecimal basePrice,
      int maxCapacity,
      LocalTime openHour,
      LocalTime closeHour,
      int durationLimitMinutes) {
    this.code = code;
    update(basePrice, maxCapacity, openHour, closeHour, durationLimitMinutes);
  }

  public void update(
      BigDecimal basePrice,
      int maxCapacity,
      LocalTime openHour,
      LocalTime closeHour,
      int durationLimitMinutes) {
    this.basePrice = basePrice;
    this.maxCapacity = maxCapacity;
    this.openHour = openHour;
    this.closeHour = closeHour;
    this.durationLimitMinutes = durationLimitMinutes;
  }

  @PrePersist
  void onCreate() {
    createdAt = updatedAt = Instant.now();
  }

  @PreUpdate
  void onUpdate() {
    updatedAt = Instant.now();
  }

  public Long getId() {
    return id;
  }

  public String getCode() {
    return code;
  }

  public BigDecimal getBasePrice() {
    return basePrice;
  }

  public int getMaxCapacity() {
    return maxCapacity;
  }

  public LocalTime getOpenHour() {
    return openHour;
  }

  public LocalTime getCloseHour() {
    return closeHour;
  }

  public int getDurationLimitMinutes() {
    return durationLimitMinutes;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
