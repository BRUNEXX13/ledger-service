package com.astropay.domain.model.outbox;

public enum OutboxEventStatus {
    UNPROCESSED, // 0
    PROCESSING,  // 1
    PROCESSED,   // 2
    FAILED       // 3
}
