package com.astropay.domain.model.outbox;

import jakarta.persistence.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tb_outbox_event")
@EntityListeners(AuditingEntityListener.class)
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
    private Instant lockedAt;

    @Column(nullable = false)
    private int retryCount = 0;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;


    protected OutboxEvent() {}

    public OutboxEvent(String aggregateType, String aggregateId, String eventType, String payload) {
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.payload = payload;
        this.createdAt = Instant.now();
    }

    // Getters
    public UUID getId() { return id; }
    public String getAggregateType() { return aggregateType; }
    public String getAggregateId() { return aggregateId; }
    public String getEventType() { return eventType; }
    public String getPayload() { return payload; }
    public OutboxEventStatus getStatus() { return status; }
    public Instant getLockedAt() { return lockedAt; }
    public int getRetryCount() { return retryCount; }
    public Instant getCreatedAt() { return createdAt; }

    // Setters
    public void setStatus(OutboxEventStatus status) { this.status = status; }
    public void setLockedAt(Instant lockedAt) { this.lockedAt = lockedAt; }
    public void incrementRetryCount() { this.retryCount++; }
    
    // Setter for testing purposes
    public void setRetryCount(int retryCount) { this.retryCount = retryCount; }

    @PrePersist
    public void prePersist() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
    }
}
