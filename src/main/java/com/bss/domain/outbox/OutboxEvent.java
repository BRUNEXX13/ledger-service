package com.bss.domain.outbox;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "tb_outbox_event", indexes = {
    @Index(name = "idx_outbox_status_eventtype_created", columnList = "status, eventType, createdAt"),
    @Index(name = "idx_outbox_locked_at", columnList = "lockedAt")
})
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String aggregateType;

    @Column(nullable = false)
    private String aggregateId;

    @Column(nullable = false)
    private String eventType;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Enumerated(EnumType.ORDINAL)
    @Column(nullable = false)
    private OutboxEventStatus status = OutboxEventStatus.UNPROCESSED;

    @Column
    private LocalDateTime lockedAt;

    @Column(nullable = false)
    private int retryCount = 0;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected OutboxEvent() {}

    public OutboxEvent(String aggregateType, String aggregateId, String eventType, String payload) {
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.payload = payload;
        this.createdAt = LocalDateTime.now();
    }

    // Getters
    public UUID getId() { return id; }
    public String getAggregateType() { return aggregateType; }
    public String getAggregateId() { return aggregateId; }
    public String getEventType() { return eventType; }
    public String getPayload() { return payload; }
    public OutboxEventStatus getStatus() { return status; }
    public LocalDateTime getLockedAt() { return lockedAt; }
    public int getRetryCount() { return retryCount; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    // Setters
    public void setStatus(OutboxEventStatus status) { this.status = status; }
    public void setLockedAt(LocalDateTime lockedAt) { this.lockedAt = lockedAt; }
    public void incrementRetryCount() { this.retryCount++; }
    
    // Setter for testing purposes
    public void setRetryCount(int retryCount) { this.retryCount = retryCount; }

    /**
     * Handles the failure logic, updating retry count and status based on max retries.
     */
    public void handleFailure(int maxRetries) {
        this.incrementRetryCount();
        this.lockedAt = null;
        
        if (this.retryCount >= maxRetries) {
            this.status = OutboxEventStatus.FAILED;
        } else {
            this.status = OutboxEventStatus.UNPROCESSED;
        }
    }
}
