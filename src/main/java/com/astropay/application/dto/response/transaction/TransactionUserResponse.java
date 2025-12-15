package com.astropay.application.dto.response.transaction;

import com.astropay.domain.model.transaction.Transaction;
import com.astropay.domain.model.user.User;

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

    // Construtor para mapeamento direto a partir da entidade
    public TransactionUserResponse(Transaction transaction) {
        User sender = transaction.getSender().getUser();
        this.transactionIdempotencyKey = transaction.getIdempotencyKey();
        this.transactionId = transaction.getId();
        this.senderName = sender.getName();
        this.senderEmail = sender.getEmail();
        this.senderDocument = sender.getDocument();
        this.transactionDate = transaction.getCreatedAt();
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
