package com.astropay.application.service.transfer;

import com.astropay.application.event.transactions.TransactionEvent;
import com.astropay.application.service.kafka.producer.KafkaProducerService;
import com.astropay.domain.model.account.Account;
import com.astropay.domain.model.account.AccountRepository;
import com.astropay.domain.model.transaction.Transaction;
import com.astropay.domain.model.transaction.TransactionRepository;
import com.astropay.domain.model.transfer.Transfer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransferServiceImpl implements TransferService {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final KafkaProducerService kafkaProducerService;

    public TransferServiceImpl(AccountRepository accountRepository, TransactionRepository transactionRepository, KafkaProducerService kafkaProducerService) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.kafkaProducerService = kafkaProducerService;
    }

    @Override
    @Transactional
    public void transfer(Transfer transfer) {
        // CORREÇÃO: Usando findByIdForUpdate para aplicar bloqueio pessimista
        Account senderAccount = accountRepository.findByIdForUpdate(transfer.getSenderAccountId())
                .orElseThrow(() -> new RuntimeException("Sender account not found"));

        Account receiverAccount = accountRepository.findByIdForUpdate(transfer.getReceiverAccountId())
                .orElseThrow(() -> new RuntimeException("Receiver account not found"));

        // A lógica de negócio de saque (incluindo verificação de saldo) está na própria entidade.
        senderAccount.withdraw(transfer.getAmount());
        receiverAccount.deposit(transfer.getAmount());

        // O save não é estritamente necessário se a transação estiver ativa,
        // mas é uma boa prática para clareza.
        accountRepository.save(senderAccount);
        accountRepository.save(receiverAccount);

        Transaction transaction = new Transaction(senderAccount, receiverAccount, transfer.getAmount(), transfer.getIdempotencyKey());
        transactionRepository.save(transaction);

        // Publica o evento para o Kafka
        TransactionEvent event = new TransactionEvent(
                transaction.getId(),
                senderAccount.getId(),
                receiverAccount.getId(),
                transfer.getAmount(),
                transaction.getCreatedAt(),
                transfer.getIdempotencyKey()
        );
        kafkaProducerService.sendTransactionEvent(event);
    }
}
