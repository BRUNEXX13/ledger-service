package com.bss.application.dto.response.transaction;

import com.bss.application.util.AppConstants;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonRootName;
import org.springframework.hateoas.RepresentationModel;
import org.springframework.hateoas.server.core.Relation;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@JsonRootName(value = "transaction")
@Relation(collectionRelation = "transactions")
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TransactionResponse extends RepresentationModel<TransactionResponse> {

    private Long id;
    private Long senderAccountId;
    private Long receiverAccountId;
    private BigDecimal amount;
    private String status; // Adicionado
    private String failureReason; // Adicionado
    private UUID idempotencyKey;

    @JsonFormat(pattern = AppConstants.DATE_TIME_FORMAT, timezone = "UTC")
    private Instant createdAt;

    public TransactionResponse() {
    }

    // Construtor atualizado
    public TransactionResponse(Long id, Long senderAccountId, Long receiverAccountId, BigDecimal amount, String status, String failureReason, UUID idempotencyKey) {
        this.id = id;
        this.senderAccountId = senderAccountId;
        this.receiverAccountId = receiverAccountId;
        this.amount = amount;
        this.status = status;
        this.failureReason = failureReason;
        this.idempotencyKey = idempotencyKey;
    }

    // Getters e Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getSenderAccountId() { return senderAccountId; }
    public void setSenderAccountId(Long senderAccountId) { this.senderAccountId = senderAccountId; }
    public Long getReceiverAccountId() { return receiverAccountId; }
    public void setReceiverAccountId(Long receiverAccountId) { this.receiverAccountId = receiverAccountId; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }
    public UUID getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(UUID idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        TransactionResponse that = (TransactionResponse) o;
        return Objects.equals(id, that.id) &&
                Objects.equals(senderAccountId, that.senderAccountId) &&
                Objects.equals(receiverAccountId, that.receiverAccountId) &&
                Objects.equals(amount, that.amount) &&
                Objects.equals(status, that.status) &&
                Objects.equals(failureReason, that.failureReason) &&
                Objects.equals(idempotencyKey, that.idempotencyKey) &&
                Objects.equals(createdAt, that.createdAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), id, senderAccountId, receiverAccountId, amount, status, failureReason, idempotencyKey, createdAt);
    }
}
