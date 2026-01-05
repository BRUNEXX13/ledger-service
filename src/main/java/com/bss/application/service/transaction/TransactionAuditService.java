package com.bss.application.service.transaction;

import com.bss.application.dto.response.transaction.TransactionUserResponse;
import com.bss.domain.model.transaction.Transaction;

public interface TransactionAuditService {
    void createAuditEvent(Transaction transaction, String eventType);
    TransactionUserResponse findUserByTransactionId(Long transactionId);
}
