package com.tonyleiva.parkinggarage.infrastructure.persistence;

import com.tonyleiva.parkinggarage.domain.spot.ParkingSpot;
import java.util.Optional;
import java.math.BigDecimal;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface ParkingSpotRepository extends JpaRepository<ParkingSpot, Long> {
  Optional<ParkingSpot> findByExternalId(Long externalId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select s from ParkingSpot s where s.latitude = :latitude and s.longitude = :longitude")
  Optional<ParkingSpot> findByCoordinatesForUpdate(BigDecimal latitude, BigDecimal longitude);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select s from ParkingSpot s where s.id = :id")
  Optional<ParkingSpot> findByIdForUpdate(Long id);

  long countByOccupiedTrue();

  long countBySectorIdAndOccupiedTrue(Long sectorId);
}
