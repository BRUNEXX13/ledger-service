package com.astropay.domain.model.outbox;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "tb_outbox_event")
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String aggregateType; // Ex: "Transaction"

    @Column(nullable = false)
    private String aggregateId; // Ex: ID da transação

    @Column(nullable = false)
    private String eventType; // Ex: "TransactionCreated"

    @Lob
    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload; // O JSON do evento

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Deprecated
    protected OutboxEvent() {}

    public OutboxEvent(String aggregateType, String aggregateId, String eventType, String payload) {
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.payload = payload;
        this.createdAt = LocalDateTime.now();
    }

    // Getters
    public UUID getId() {
        return id;
    }

    public String getAggregateType() {
        return aggregateType;
    }

    public String getAggregateId() {
        return aggregateId;
    }

    public String getEventType() {
        return eventType;
    }

    public String getPayload() {
        return payload;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
