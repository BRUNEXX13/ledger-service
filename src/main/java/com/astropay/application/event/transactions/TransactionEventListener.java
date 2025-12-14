package com.astropay.application.event.transactions;


import com.astropay.application.event.account.AccountCreatedEvent;
import com.astropay.application.service.notification.EmailService;
import com.astropay.domain.model.account.AccountRepository;
import com.astropay.domain.model.user.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class TransactionEventListener {

    private static final Logger log = LoggerFactory.getLogger(TransactionEventListener.class);

    private final EmailService emailService;
    private final AccountRepository accountRepository;
    private final ObjectMapper objectMapper;

    public TransactionEventListener(EmailService emailService, AccountRepository accountRepository, ObjectMapper objectMapper) {
        this.emailService = emailService;
        this.accountRepository = accountRepository;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "transactions", groupId = "ledger-notification-group")
    public void handleTransactionEvent(Object message) {
        try {
            TransactionEvent event = objectMapper.convertValue(message, TransactionEvent.class);
            log.info("Evento de TRANSAÇÃO recebido para notificação. IdempotencyKey: {}", event.getIdempotencyKey());

            User sender = accountRepository.findById(event.getSenderAccountId())
                    .orElseThrow(() -> new RuntimeException("Conta do remetente não encontrada"))
                    .getUser();

            User receiver = accountRepository.findById(event.getReceiverAccountId())
                    .orElseThrow(() -> new RuntimeException("Conta do destinatário não encontrada"))
                    .getUser();

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

    @KafkaListener(topics = "accounts", groupId = "ledger-notification-group")
    public void handleAccountCreatedEvent(Object message) {
        try {
            AccountCreatedEvent event = objectMapper.convertValue(message, AccountCreatedEvent.class);
            log.info("Evento de CONTA CRIADA recebido para notificação. AccountId: {}", event.getAccountId());

            String subject = "Bem-vindo ao Nosso Banco!";
            String body = String.format(
                    "Olá, %s! Sua conta foi criada com sucesso. Seu ID de conta é %d.",
                    event.getUserName(), event.getAccountId()
            );
            emailService.sendTransactionNotification(event.getUserEmail(), subject, body);

        } catch (Exception e) {
            log.error("Erro ao processar evento de CRIAÇÃO DE CONTA: {}", e.getMessage(), e);
            throw new RuntimeException("Falha no processamento do evento de criação de conta", e);
        }
    }
}
