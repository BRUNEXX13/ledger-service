package com.astropay.application.service.transaction;

import com.astropay.application.dto.response.transaction.TransactionUserResponse;
import com.astropay.domain.model.transaction.Transaction;

public interface TransactionAuditService {
    void createAuditEvent(Transaction transaction, String eventType);
    TransactionUserResponse findUserByTransactionId(Long transactionId);
}
