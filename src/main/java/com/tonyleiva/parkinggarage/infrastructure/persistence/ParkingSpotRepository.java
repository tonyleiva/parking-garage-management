package com.tonyleiva.parkinggarage.infrastructure.persistence;

import com.tonyleiva.parkinggarage.domain.spot.ParkingSpot;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ParkingSpotRepository extends JpaRepository<ParkingSpot, Long> {
  Optional<ParkingSpot> findByExternalId(Long externalId);
}
