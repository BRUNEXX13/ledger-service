package com.astropay.application.service.transaction;

import com.astropay.application.dto.response.transaction.TransactionUserResponse;
import com.astropay.domain.model.transaction.Transaction;
import com.astropay.domain.model.transaction.TransactionRepository;
import com.astropay.domain.model.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class TransactionAuditServiceImpl implements TransactionAuditService {

    private final TransactionRepository transactionRepository;

    public TransactionAuditServiceImpl(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    @Override
    public TransactionUserResponse findUserByTransactionId(Long transactionId) {
        // O fetch EAGER para sender e user garante que o JPA traga tudo em uma única query otimizada.
        Transaction transaction = transactionRepository.findByIdWithSenderAndUser(transactionId)
                .orElseThrow(() -> new RuntimeException("Transaction not found with id: " + transactionId));

        User sender = transaction.getSender().getUser();

        return new TransactionUserResponse(
                transaction.getIdempotencyKey(),
                transaction.getId(),
                sender.getName(),
                sender.getEmail(),
                sender.getDocument(),
                transaction.getCreatedAt()
        );
    }
}
