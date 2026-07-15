package com.tonyleiva.parkinggarage.infrastructure.persistence;

import com.tonyleiva.parkinggarage.domain.sector.GarageSector;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GarageSectorRepository extends JpaRepository<GarageSector, Long> {
  Optional<GarageSector> findByCode(String code);
}
