package com.bss.application.service.transaction.port.in;

import com.bss.application.dto.response.transaction.TransactionResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;

public interface TransactionService {
    Slice<TransactionResponse> findAllTransactions(Pageable pageable);
    TransactionResponse findTransactionById(Long id);
}
