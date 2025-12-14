package com.astropay.application.service.transfer;

import com.astropay.application.event.transactions.TransactionEvent;
import com.astropay.application.exception.JsonSerializationException;
import com.astropay.application.exception.ResourceNotFoundException;
import com.astropay.domain.model.account.Account;
import com.astropay.domain.model.account.AccountRepository;
import com.astropay.domain.model.account.InsufficientBalanceException;
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

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

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
    public Transaction transfer(Transfer transfer) {
        // 1. Checagem de Idempotência (usando o cache)
        Optional<Transaction> existingTransaction = transactionRepository.findByIdempotencyKey(transfer.getIdempotencyKey());
        if (existingTransaction.isPresent()) {
            log.warn("Idempotency key {} already processed. Returning existing transaction {}.", transfer.getIdempotencyKey(), existingTransaction.get().getId());
            return existingTransaction.get();
        }

        if (transfer.getSenderAccountId().equals(transfer.getReceiverAccountId())) {
            throw new IllegalArgumentException("Sender and receiver accounts cannot be the same.");
        }

        // 2. Busca as contas (usando o cache)
        List<Long> accountIds = List.of(transfer.getSenderAccountId(), transfer.getReceiverAccountId());
        Map<Long, Account> accounts = accountRepository.findByIds(accountIds).stream()
                .collect(Collectors.toMap(Account::getId, Function.identity()));

        Account senderAccount = accounts.get(transfer.getSenderAccountId());
        if (senderAccount == null) throw new ResourceNotFoundException("Sender account not found with id: " + transfer.getSenderAccountId());

        Account receiverAccount = accounts.get(transfer.getReceiverAccountId());
        if (receiverAccount == null) throw new ResourceNotFoundException("Receiver account not found with id: " + transfer.getReceiverAccountId());

        // 3. Cria a transação com status PENDING
        Transaction transaction = new Transaction(senderAccount, receiverAccount, transfer.getAmount(), transfer.getIdempotencyKey());
        transactionRepository.save(transaction);
        log.info("Transaction {} created with status PENDING.", transaction.getId());

        try {
            // 4. Tenta executar a lógica de negócio
            senderAccount.withdraw(transfer.getAmount());
            receiverAccount.deposit(transfer.getAmount());

            // 5. Salva as contas, o que irá ATUALIZAR o cache via @CachePut
            accountRepository.save(senderAccount);
            accountRepository.save(receiverAccount);

            // 6. Se sucesso, marca como COMPLETED
            transaction.complete();
            log.info("Transaction {} COMPLETED successfully.", transaction.getId());

            // 7. Cria o evento Outbox apenas em caso de sucesso
            createOutboxEvent(transaction);

        } catch (InsufficientBalanceException | IllegalStateException e) {
            log.warn("Transaction {} FAILED. Reason: {}", transaction.getId(), e.getMessage());
            transaction.fail(e.getMessage());
        }
        
        // 8. Salva o estado final da transação (COMPLETED ou FAILED), atualizando o cache
        return transactionRepository.save(transaction);
    }

    private void createOutboxEvent(Transaction transaction) {
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
            throw new JsonSerializationException("Failed to serialize transaction event to JSON", e);
        }

        OutboxEvent outboxEvent = new OutboxEvent(
                "Transaction",
                transaction.getId().toString(),
                "TransactionCompleted",
                payload
        );
        outboxEventRepository.save(outboxEvent);
        log.info("Outbox event created for transaction {}.", transaction.getId());
    }
}
