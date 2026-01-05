package com.bss.application.service.transaction;

import com.bss.application.dto.response.transaction.TransactionUserResponse;
import com.bss.application.event.transactions.TransactionEvent;
import com.bss.application.exception.ResourceNotFoundException;
import com.bss.domain.outbox.OutboxEvent;
import com.bss.domain.outbox.OutboxEventRepository;
import com.bss.domain.transaction.Transaction;
import com.bss.domain.transaction.TransactionRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransactionAuditServiceImpl implements TransactionAuditService {

    private static final Logger log = LoggerFactory.getLogger(TransactionAuditServiceImpl.class);

    private final OutboxEventRepository outboxEventRepository;
    private final TransactionRepository transactionRepository;
    private final ObjectMapper objectMapper;

    public TransactionAuditServiceImpl(OutboxEventRepository outboxEventRepository,
                                       TransactionRepository transactionRepository,
                                       ObjectMapper objectMapper) {
        this.outboxEventRepository = outboxEventRepository;
        this.transactionRepository = transactionRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public void createAuditEvent(Transaction transaction, String eventType) {
        TransactionEvent event = new TransactionEvent(
                transaction.getId(),
                transaction.getSender().getId(),
                transaction.getReceiver().getId(),
                transaction.getAmount(),
                transaction.getCreatedAt(),
                transaction.getIdempotencyKey()
        );

        String payload;
        try {
            payload = objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize transaction event to JSON for transaction {}", transaction.getId(), e);
            return;
        }

        OutboxEvent outboxEvent = new OutboxEvent(
                "Transaction",
                transaction.getId().toString(),
                eventType,
                payload
        );
        outboxEventRepository.save(outboxEvent);
        log.info("Outbox audit event '{}' created for transaction {}.", eventType, transaction.getId());
    }

    @Override
    @Transactional(readOnly = true)
    public TransactionUserResponse findUserByTransactionId(Long transactionId) {
        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found with id: " + transactionId));

        return new TransactionUserResponse(transaction);
    }
}
