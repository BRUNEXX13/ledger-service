package com.astropay.application.dto.response.transaction;

import java.time.LocalDateTime;
import java.util.UUID;

public class TransactionUserResponse {

    private UUID transactionIdempotencyKey;
    private Long transactionId;
    private String senderName;
    private String senderEmail;
    private String senderDocument;
    private LocalDateTime transactionDate;

    public TransactionUserResponse() {
    }

    public TransactionUserResponse(UUID transactionIdempotencyKey, Long transactionId, String senderName, String senderEmail, String senderDocument, LocalDateTime transactionDate) {
        this.transactionIdempotencyKey = transactionIdempotencyKey;
        this.transactionId = transactionId;
        this.senderName = senderName;
        this.senderEmail = senderEmail;
        this.senderDocument = senderDocument;
        this.transactionDate = transactionDate;
    }

    public UUID getTransactionIdempotencyKey() {
        return transactionIdempotencyKey;
    }

    public Long getTransactionId() {
        return transactionId;
    }

    public String getSenderName() {
        return senderName;
    }

    public String getSenderEmail() {
        return senderEmail;
    }

    public String getSenderDocument() {
        return senderDocument;
    }

    public LocalDateTime getTransactionDate() {
        return transactionDate;
    }
}
