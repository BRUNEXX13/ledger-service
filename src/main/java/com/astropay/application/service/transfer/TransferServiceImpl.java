package com.astropay.application.service.transfer;

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

    public TransferServiceImpl(AccountRepository accountRepository, TransactionRepository transactionRepository) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
    }

    @Override
    @Transactional
    public void transfer(Transfer transfer) {
        // Busca as contas pelo ID da conta, travando-as para a transação.
        // O bloqueio pessimista (PESSIMISTIC_WRITE) é mais seguro para operações financeiras.
        Account senderAccount = accountRepository.findById(transfer.getSenderAccountId())
                .orElseThrow(() -> new RuntimeException("Sender account not found"));

        Account receiverAccount = accountRepository.findById(transfer.getReceiverAccountId())
                .orElseThrow(() -> new RuntimeException("Receiver account not found"));

        // A lógica de negócio de saque (incluindo verificação de saldo) está na própria entidade.
        senderAccount.withdraw(transfer.getAmount());
        receiverAccount.deposit(transfer.getAmount());

        // O save não é estritamente necessário se a transação estiver ativa,
        // mas é uma boa prática para clareza.
        accountRepository.save(senderAccount);
        accountRepository.save(receiverAccount);

        // Cria a transação usando as entidades, mantendo a integridade referencial.
        Transaction transaction = new Transaction(senderAccount, receiverAccount, transfer.getAmount(), transfer.getIdempotencyKey());
        transactionRepository.save(transaction);
    }
}