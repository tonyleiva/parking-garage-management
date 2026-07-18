package com.tonyleiva.parkinggarage.infrastructure.persistence;

import com.tonyleiva.parkinggarage.domain.sector.GarageSector;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface GarageSectorRepository extends JpaRepository<GarageSector, Long> {
  Optional<GarageSector> findByCode(String code);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select s from GarageSector s where s.id = :id")
  Optional<GarageSector> findByIdForUpdate(Long id);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select s from GarageSector s order by s.id")
  java.util.List<GarageSector> findAllForUpdateOrderById();
}
