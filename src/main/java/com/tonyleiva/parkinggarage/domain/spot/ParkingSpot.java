package com.tonyleiva.parkinggarage.domain.spot;

import com.tonyleiva.parkinggarage.domain.sector.GarageSector;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(
    name = "parking_spot",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_parking_spot_coordinates",
            columnNames = {"latitude", "longitude"}))
public class ParkingSpot {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "external_id", nullable = false, unique = true)
  private Long externalId;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "sector_id", nullable = false)
  private GarageSector sector;

  @Column(nullable = false, precision = 11, scale = 8)
  private BigDecimal latitude;

  @Column(nullable = false, precision = 11, scale = 8)
  private BigDecimal longitude;

  @Column(nullable = false)
  private boolean occupied;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected ParkingSpot() {}

  public ParkingSpot(
      Long externalId,
      GarageSector sector,
      BigDecimal latitude,
      BigDecimal longitude,
      boolean occupied) {
    this.externalId = externalId;
    update(sector, latitude, longitude, occupied);
  }

  public void update(
      GarageSector sector, BigDecimal latitude, BigDecimal longitude, boolean occupied) {
    this.sector = sector;
    this.latitude = latitude;
    this.longitude = longitude;
    this.occupied = occupied;
  }

  public void occupy() {
    if (occupied) throw new IllegalStateException("A vaga já está ocupada.");
    occupied = true;
  }

  public void release() {
    if (!occupied) throw new IllegalStateException("A vaga já está livre.");
    occupied = false;
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

  public Long getExternalId() {
    return externalId;
  }

  public GarageSector getSector() {
    return sector;
  }

  public BigDecimal getLatitude() {
    return latitude;
  }

  public BigDecimal getLongitude() {
    return longitude;
  }

  public boolean isOccupied() {
    return occupied;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
