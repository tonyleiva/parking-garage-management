package com.tonyleiva.parkinggarage.infrastructure.persistence;

import com.tonyleiva.parkinggarage.domain.session.ParkingSession;
import com.tonyleiva.parkinggarage.domain.session.ParkingSessionStatus;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ParkingSessionRepository extends JpaRepository<ParkingSession, Long> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select s from ParkingSession s where s.licensePlate = :plate"
          + " and s.status in :statuses")
  Optional<ParkingSession> findActiveForUpdate(
      @Param("plate") String plate, @Param("statuses") Iterable<ParkingSessionStatus> statuses);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select s from ParkingSession s where s.licensePlate = :plate and s.status = :status")
  Optional<ParkingSession> findByPlateAndStatusForUpdate(
      @Param("plate") String plate, @Param("status") ParkingSessionStatus status);

  long countByStatus(ParkingSessionStatus status);

}
