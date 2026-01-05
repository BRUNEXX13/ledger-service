package com.bss.application.event.transactions;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public class TransactionEvent {

    private Long transactionId;
    private Long senderAccountId;
    private Long receiverAccountId;
    private BigDecimal amount;
    private Instant timestamp;
    private UUID idempotencyKey;

    /**
     * Construtor padrão para deserialização pelo Jackson/Kafka.
     */
    public TransactionEvent() {
    }

    public TransactionEvent(Long transactionId, Long senderAccountId, Long receiverAccountId, BigDecimal amount, Instant timestamp, UUID idempotencyKey) {
        this.transactionId = transactionId;
        this.senderAccountId = senderAccountId;
        this.receiverAccountId = receiverAccountId;
        this.amount = amount;
        this.timestamp = timestamp;
        this.idempotencyKey = idempotencyKey;
    }

    // Getters and Setters
    public Long getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(Long transactionId) {
        this.transactionId = transactionId;
    }

    public Long getSenderAccountId() {
        return senderAccountId;
    }

    public void setSenderAccountId(Long senderAccountId) {
        this.senderAccountId = senderAccountId;
    }

    public Long getReceiverAccountId() {
        return receiverAccountId;
    }

    public void setReceiverAccountId(Long receiverAccountId) {
        this.receiverAccountId = receiverAccountId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public UUID getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(UUID idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }
}
