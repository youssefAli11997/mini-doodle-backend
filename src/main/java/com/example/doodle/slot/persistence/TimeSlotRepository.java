package com.example.doodle.slot.persistence;

import com.example.doodle.slot.domain.SlotStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TimeSlotRepository extends JpaRepository<TimeSlotEntity, UUID> {

    Optional<TimeSlotEntity> findByIdAndUserId(UUID id, UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM TimeSlotEntity s WHERE s.id = :id")
    Optional<TimeSlotEntity> findByIdForUpdate(@Param("id") UUID id);

    @Query("""
        SELECT s FROM TimeSlotEntity s
        WHERE s.userId = :userId
          AND s.startTime < :to
          AND s.endTime > :from
          AND (:status IS NULL OR s.status = :status)
        ORDER BY s.startTime ASC
    """)
    List<TimeSlotEntity> findByUserIdAndTimeRange(
            @Param("userId") UUID userId,
            @Param("from") Instant from,
            @Param("to") Instant to,
            @Param("status") SlotStatus status
    );
}
