package com.astropay.application.service.transaction;

import com.astropay.application.dto.response.transaction.TransactionUserResponse;

public interface TransactionAuditService {

    /**
     * Encontra o usuário que iniciou uma transação específica.
     *
     * @param transactionId O ID da transação.
     * @return Um DTO com os detalhes do usuário remetente.
     */
    TransactionUserResponse findUserByTransactionId(Long transactionId);
}
