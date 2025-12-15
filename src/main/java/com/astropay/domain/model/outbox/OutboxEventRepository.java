package com.astropay.domain.model.outbox;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM OutboxEvent e WHERE e.status = :status AND e.eventType = :eventType AND (e.lockedAt IS NULL OR e.lockedAt < :lockTimeout) ORDER BY e.createdAt ASC")
    List<OutboxEvent> findAndLockUnprocessedEvents(@Param("status") OutboxEventStatus status,
                                                   @Param("eventType") String eventType,
                                                   @Param("lockTimeout") LocalDateTime lockTimeout,
                                                   org.springframework.data.domain.Pageable pageable);

    List<OutboxEvent> findByEventType(String eventType);
}
