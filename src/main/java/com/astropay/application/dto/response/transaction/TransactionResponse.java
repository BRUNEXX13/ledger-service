package com.astropay.application.dto.response.transaction;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonRootName;
import org.springframework.hateoas.RepresentationModel;
import org.springframework.hateoas.server.core.Relation;

import java.math.BigDecimal;
import java.time.LocalDateTime;
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

    @JsonFormat(pattern = "MM/dd/yyyy HH:mm:ss.SSS")
    private LocalDateTime createdAt;

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
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
