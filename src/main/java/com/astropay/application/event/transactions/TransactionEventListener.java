package com.astropay.application.event.transactions;

import com.astropay.application.service.notification.EmailService;
import com.astropay.domain.model.user.User;
import com.astropay.domain.model.user.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class TransactionEventListener {

    private static final Logger log = LoggerFactory.getLogger(TransactionEventListener.class);

    private final EmailService emailService;
    private final UserRepository userRepository; // Alterado de AccountRepository para UserRepository
    private final ObjectMapper objectMapper;

    public TransactionEventListener(EmailService emailService, UserRepository userRepository, ObjectMapper objectMapper) {
        this.emailService = emailService;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    @KafkaListener(topics = "transactions", groupId = "ledger-notification-group")
    public void handleTransactionEvent(ConsumerRecord<String, TransactionEvent> record) {
        try {
            TransactionEvent event = record.value();
            log.info("Evento de TRANSAÇÃO recebido para notificação. IdempotencyKey: {}", event.getIdempotencyKey());

            // Busca o usuário diretamente pelo ID (assumindo que accountId == userId)
            User sender = userRepository.findById(event.getSenderAccountId())
                    .orElseThrow(() -> new RuntimeException("Usuário remetente não encontrado"));

            User receiver = userRepository.findById(event.getReceiverAccountId())
                    .orElseThrow(() -> new RuntimeException("Usuário destinatário não encontrado"));

            String senderSubject = "Comprovante de Transferência Enviada";
            String senderBody = String.format(
                "Olá, %s! Você enviou %.2f para %s.",
                sender.getName(), event.getAmount(), receiver.getName()
            );
            emailService.sendTransactionNotification(sender.getEmail(), senderSubject, senderBody);

            String receiverSubject = "Você Recebeu uma Transferência";
            String receiverBody = String.format(
                "Olá, %s! Você recebeu %.2f de %s.",
                receiver.getName(), event.getAmount(), sender.getName()
            );
            emailService.sendTransactionNotification(receiver.getEmail(), receiverSubject, receiverBody);

        } catch (Exception e) {
            log.error("Erro ao processar evento de TRANSAÇÃO: {}", e.getMessage(), e);
            throw new RuntimeException("Falha no processamento do evento de transação", e);
        }
    }
}
