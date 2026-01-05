package com.bss.application.service.transaction.port.in;

import com.bss.application.dto.response.transaction.TransactionResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface TransactionService {
    Page<TransactionResponse> findAllTransactions(Pageable pageable);
    TransactionResponse findTransactionById(Long id);
}
