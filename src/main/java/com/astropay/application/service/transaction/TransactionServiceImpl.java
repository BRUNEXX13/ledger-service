package com.astropay.application.service.transaction;

import com.astropay.application.controller.transaction.mapper.TransactionMapper;
import com.astropay.application.dto.response.transaction.TransactionResponse;
import com.astropay.application.service.transaction.port.in.TransactionService;
import com.astropay.domain.model.transaction.TransactionRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class TransactionServiceImpl implements TransactionService {

    private final TransactionRepository transactionRepository;
    private final TransactionMapper transactionMapper;

    public TransactionServiceImpl(TransactionRepository transactionRepository, TransactionMapper transactionMapper) {
        this.transactionRepository = transactionRepository;
        this.transactionMapper = transactionMapper;
    }

    @Override
    public Page<TransactionResponse> findAllTransactions(Pageable pageable) {
        return transactionRepository.findAll(pageable)
            .map(transactionMapper::toTransactionResponse);
    }

    @Override
    public TransactionResponse findTransactionById(Long id) {
        return transactionRepository.findById(id)
            .map(transactionMapper::toTransactionResponse)
            .orElseThrow(() -> new RuntimeException("Transaction not found with id: " + id));
    }
}
