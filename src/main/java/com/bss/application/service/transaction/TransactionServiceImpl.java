package com.bss.application.service.transaction;

import com.bss.application.controller.transaction.mapper.TransactionMapper;
import com.bss.application.dto.response.transaction.TransactionResponse;
import com.bss.application.exception.ResourceNotFoundException;
import com.bss.application.service.transaction.port.in.TransactionService;
import com.bss.domain.transaction.TransactionRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
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
    public Slice<TransactionResponse> findAllTransactions(Pageable pageable) {
        // Using findAll(pageable) returns a Page, which we can treat as a Slice.
        // Ideally, the repository should return Slice to avoid the count query if possible,
        // but JpaRepository.findAll(Pageable) always returns Page (and executes count).
        // To strictly avoid count, we would need a custom query or findBy... returning Slice.
        // However, changing the return type to Slice in the Service contract allows future optimization
        // without breaking the API contract, and Slice is lighter for the client (no total pages).
        return transactionRepository.findAll(pageable)
            .map(transactionMapper::toTransactionResponse);
    }

    @Override
    @Cacheable(value = "transactions", key = "#id")
    public TransactionResponse findTransactionById(Long id) {
        return transactionRepository.findById(id)
            .map(transactionMapper::toTransactionResponse)
            .orElseThrow(() -> new ResourceNotFoundException("Transaction not found with id: " + id));
    }
}
