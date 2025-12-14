package com.astropay.application.service.transfer;

import com.astropay.application.event.transactions.TransactionEvent;
import com.astropay.application.exception.JsonSerializationException;
import com.astropay.application.exception.ResourceNotFoundException;
import com.astropay.domain.model.account.Account;
import com.astropay.domain.model.account.AccountRepository;
import com.astropay.domain.model.outbox.OutboxEvent;
import com.astropay.domain.model.outbox.OutboxEventRepository;
import com.astropay.domain.model.transaction.Transaction;
import com.astropay.domain.model.transaction.TransactionRepository;
import com.astropay.domain.model.transfer.Transfer;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransferServiceImpl implements TransferService {

    private static final Logger log = LoggerFactory.getLogger(TransferServiceImpl.class);


    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    public TransferServiceImpl(AccountRepository accountRepository,
                               TransactionRepository transactionRepository,
                               OutboxEventRepository outboxEventRepository,
                               ObjectMapper objectMapper) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    @Retryable(
            value = {OptimisticLockingFailureException.class},
            maxAttempts = 5,
            backoff = @Backoff(delay = 100)
    )
    public void transfer(Transfer transfer) {
        if (transfer.getSenderAccountId().equals(transfer.getReceiverAccountId())) {
            throw new IllegalArgumentException("Sender and receiver accounts cannot be the same.");
        }

        Account senderAccount = accountRepository.findById(transfer.getSenderAccountId())
                .orElseThrow(() -> new ResourceNotFoundException("Sender account not found with id: " + transfer.getSenderAccountId()));

        Account receiverAccount = accountRepository.findById(transfer.getReceiverAccountId())
                .orElseThrow(() -> new ResourceNotFoundException("Receiver account not found with id: " + transfer.getReceiverAccountId()));

        senderAccount.withdraw(transfer.getAmount());
        receiverAccount.deposit(transfer.getAmount());

        Transaction transaction = new Transaction(senderAccount, receiverAccount, transfer.getAmount(), transfer.getIdempotencyKey());
        transactionRepository.save(transaction);
        log.info("Save on Database " + transaction);

        // --- Padrão Outbox ---
        // 1. Criar o evento de domínio
        TransactionEvent event = new TransactionEvent(
                transaction.getId(),
                senderAccount.getId(),
                receiverAccount.getId(),
                transfer.getAmount(),
                transaction.getCreatedAt(),
                transfer.getIdempotencyKey()
        );

        // 2. Serializar o evento para JSON
        String payload;
        try {
            payload = objectMapper.writeValueAsString(event);
        } catch (
                JsonProcessingException e) {
            throw new JsonSerializationException("Failed to serialize transaction event to JSON", e);
        }

        // 3. Salvar o evento na tabela outbox (dentro da mesma transação)
        OutboxEvent outboxEvent = new OutboxEvent(
                "Transaction",
                transaction.getId().toString(),
                "TransactionCreated",
                payload
        );
        outboxEventRepository.save(outboxEvent);
        log.info("Save on OutboxEvent " + outboxEvent);
    }
}
