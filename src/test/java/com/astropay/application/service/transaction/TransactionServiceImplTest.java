package com.astropay.application.service.transaction;

import com.astropay.application.controller.transaction.mapper.TransactionMapper;
import com.astropay.application.dto.response.transaction.TransactionResponse;
import com.astropay.application.exception.ResourceNotFoundException;
import com.astropay.domain.model.transaction.Transaction;
import com.astropay.domain.model.transaction.TransactionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionServiceImplTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private TransactionMapper transactionMapper;

    @InjectMocks
    private TransactionServiceImpl transactionService;

    @Test
    @DisplayName("Should find transaction by ID successfully")
    void shouldFindTransactionById() {
        // Arrange
        Long transactionId = 1L;
        Transaction transaction = mock(Transaction.class);
        TransactionResponse response = mock(TransactionResponse.class);

        when(transactionRepository.findById(transactionId)).thenReturn(Optional.of(transaction));
        when(transactionMapper.toTransactionResponse(transaction)).thenReturn(response);

        // Act
        TransactionResponse result = transactionService.findTransactionById(transactionId);

        // Assert
        assertNotNull(result);
        assertEquals(response, result);
    }

    @Test
    @DisplayName("Should throw exception when transaction ID not found")
    void shouldThrowExceptionWhenTransactionIdNotFound() {
        // Arrange
        Long transactionId = 99L;
        when(transactionRepository.findById(transactionId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(ResourceNotFoundException.class, () -> transactionService.findTransactionById(transactionId));
    }

    @Test
    @DisplayName("Should find all transactions with pagination")
    void shouldFindAllTransactions() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 10);
        Transaction transaction = mock(Transaction.class);
        TransactionResponse response = mock(TransactionResponse.class);
        Page<Transaction> transactionPage = new PageImpl<>(Collections.singletonList(transaction));

        when(transactionRepository.findAll(pageable)).thenReturn(transactionPage);
        when(transactionMapper.toTransactionResponse(any(Transaction.class))).thenReturn(response);

        // Act
        Page<TransactionResponse> result = transactionService.findAllTransactions(pageable);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals(response, result.getContent().get(0));
    }
}
