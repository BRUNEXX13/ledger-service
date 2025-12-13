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
        return new TransactionResponse(
            transaction.getId(),
            transaction.getSender().getId(), // Extrai o ID da conta
            transaction.getReceiver().getId(), // Extrai o ID da conta
            transaction.getAmount(),
            transaction.getIdempotencyKey(),
            transaction.getCreatedAt()
        );
    }
}
