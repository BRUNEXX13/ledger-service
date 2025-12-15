package com.astropay.domain.model.outbox;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    // Using native query for PostgreSQL's FOR UPDATE SKIP LOCKED
    @Query(value = "SELECT * FROM tb_outbox_event e WHERE e.status = :#{#status.ordinal()} AND e.event_type = :eventType AND (e.locked_at IS NULL OR e.locked_at < :lockTimeout) ORDER BY e.created_at ASC FOR UPDATE SKIP LOCKED", nativeQuery = true)
    List<OutboxEvent> findAndLockUnprocessedEvents(@Param("status") OutboxEventStatus status,
                                                   @Param("eventType") String eventType,
                                                   @Param("lockTimeout") LocalDateTime lockTimeout,
                                                   Pageable pageable);

    List<OutboxEvent> findByEventType(String eventType);
}
