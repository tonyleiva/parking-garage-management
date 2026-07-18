package com.tonyleiva.parkinggarage.domain.session;

import com.tonyleiva.parkinggarage.domain.sector.GarageSector;
import com.tonyleiva.parkinggarage.domain.spot.ParkingSpot;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "parking_session")
public class ParkingSession {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "license_plate", nullable = false, length = 20)
  private String licensePlate;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "sector_id")
  private GarageSector sector;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "spot_id")
  private ParkingSpot spot;

  @Column(name = "entry_time", nullable = false)
  private Instant entryTime;

  @Column(name = "parked_at")
  private Instant parkedAt;

  @Column(name = "exit_time")
  private Instant exitTime;

  @Column(name = "base_price", precision = 19, scale = 4)
  private BigDecimal basePrice;

  @Column(name = "price_multiplier", precision = 5, scale = 2)
  private BigDecimal priceMultiplier;

  @Column(name = "hourly_price", precision = 19, scale = 4)
  private BigDecimal hourlyPrice;

  @Column(precision = 19, scale = 4)
  private BigDecimal amount;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private ParkingSessionStatus status;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected ParkingSession() {}

  public ParkingSession(String licensePlate, Instant entryTime) {
    this.licensePlate = licensePlate;
    this.entryTime = entryTime;
    this.status = ParkingSessionStatus.ENTERED;
  }

  public void park(
      GarageSector sector,
      ParkingSpot spot,
      Instant parkedAt,
      BigDecimal basePrice,
      BigDecimal priceMultiplier,
      BigDecimal hourlyPrice) {
    requireStatus(ParkingSessionStatus.ENTERED, "estacionar");
    this.sector = sector;
    this.spot = spot;
    this.parkedAt = parkedAt;
    this.basePrice = basePrice;
    this.priceMultiplier = priceMultiplier;
    this.hourlyPrice = hourlyPrice;
    this.status = ParkingSessionStatus.PARKED;
  }

  public void finish(Instant exitTime, BigDecimal amount) {
    requireStatus(ParkingSessionStatus.PARKED, "finalizar");
    if (exitTime.isBefore(entryTime)) {
      throw new InvalidSessionTransitionException(
          "O horário de saída não pode ser anterior ao horário de entrada.");
    }
    this.exitTime = exitTime;
    this.amount = amount;
    this.status = ParkingSessionStatus.FINISHED;
  }

  private void requireStatus(ParkingSessionStatus expected, String operation) {
    if (status != expected) {
      throw new InvalidSessionTransitionException(
          "Não é possível " + operation + " uma sessão no estado " + status + ".");
    }
  }

  @PrePersist
  void onCreate() {
    createdAt = updatedAt = Instant.now();
  }

  @PreUpdate
  void onUpdate() {
    updatedAt = Instant.now();
  }

  public Long getId() { return id; }
  public String getLicensePlate() { return licensePlate; }
  public GarageSector getSector() { return sector; }
  public ParkingSpot getSpot() { return spot; }
  public Instant getEntryTime() { return entryTime; }
  public Instant getParkedAt() { return parkedAt; }
  public Instant getExitTime() { return exitTime; }
  public BigDecimal getBasePrice() { return basePrice; }
  public BigDecimal getPriceMultiplier() { return priceMultiplier; }
  public BigDecimal getHourlyPrice() { return hourlyPrice; }
  public BigDecimal getAmount() { return amount; }
  public ParkingSessionStatus getStatus() { return status; }
  public Instant getCreatedAt() { return createdAt; }
  public Instant getUpdatedAt() { return updatedAt; }
}
