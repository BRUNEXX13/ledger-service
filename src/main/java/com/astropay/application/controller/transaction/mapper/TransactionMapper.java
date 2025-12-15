package com.astropay.application.controller.transaction.mapper;

import com.astropay.application.dto.response.transaction.TransactionResponse;
import com.astropay.domain.model.transaction.Transaction;
import org.springframework.stereotype.Component;

@Component
public class TransactionMapper {

    public TransactionResponse toTransactionResponse(Transaction transaction) {
        if (transaction == null) {
            return null;
        }
        TransactionResponse response = new TransactionResponse(
            transaction.getId(),
            transaction.getSender().getId(),
            transaction.getReceiver().getId(),
            transaction.getAmount(),
            transaction.getStatus().toString(),
            transaction.getFailureReason(),
            transaction.getIdempotencyKey()
        );
        response.setCreatedAt(transaction.getCreatedAt()); // Corrigido
        return response;
    }
}
