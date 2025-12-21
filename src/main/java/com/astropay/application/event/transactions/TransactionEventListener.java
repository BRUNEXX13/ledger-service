package com.astropay.application.event.transactions;

import com.astropay.application.service.notification.EmailService;
import com.astropay.domain.model.user.User;
import com.astropay.domain.model.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class TransactionEventListener {

    private static final Logger log = LoggerFactory.getLogger(TransactionEventListener.class);

    private final EmailService emailService;
    private final UserRepository userRepository;

    public TransactionEventListener(EmailService emailService, UserRepository userRepository) {
        this.emailService = emailService;
        this.userRepository = userRepository;
    }

    @Transactional
    @KafkaListener(topics = "transactions", groupId = "${spring.kafka.consumer.group-id}")
    public void handleTransactionEvent(@Payload TransactionEvent event) {
        log.info("Evento de TRANSAÇÃO recebido para notificação. IdempotencyKey: {}", event.getIdempotencyKey());

        User sender = userRepository.findById(event.getSenderAccountId())
                .orElseThrow(() -> new RuntimeException("Usuário remetente não encontrado para o ID: " + event.getSenderAccountId()));

        User receiver = userRepository.findById(event.getReceiverAccountId())
                .orElseThrow(() -> new RuntimeException("Usuário destinatário não encontrado para o ID: " + event.getReceiverAccountId()));

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
    }
}
