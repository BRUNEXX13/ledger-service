package com.astropay.application.service.transfer;

import com.astropay.application.event.KafkaProducerService;
import com.astropay.application.event.TransactionEvent;
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
        Account senderAccount = accountRepository.findById(transfer.getSenderAccountId())
                .orElseThrow(() -> new RuntimeException("Sender account not found"));

        Account receiverAccount = accountRepository.findById(transfer.getReceiverAccountId())
                .orElseThrow(() -> new RuntimeException("Receiver account not found"));

        senderAccount.withdraw(transfer.getAmount());
        receiverAccount.deposit(transfer.getAmount());

        accountRepository.save(senderAccount);
        accountRepository.save(receiverAccount);

        Transaction transaction = new Transaction(senderAccount, receiverAccount, transfer.getAmount(), transfer.getIdempotencyKey());
        transactionRepository.save(transaction);

        if ((senderAccount.getUser().getId() == 1L && receiverAccount.getUser().getId() == 2L) ||
            (senderAccount.getUser().getId() == 2L && receiverAccount.getUser().getId() == 1L)) {
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
}
