package com.astropay.application.event.account;

import com.astropay.application.service.notification.EmailService;
import com.astropay.domain.model.account.AccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class AccountCreatedEventListener {

    private static final Logger log = LoggerFactory.getLogger(AccountCreatedEventListener.class);

    private final EmailService emailService;

    public AccountCreatedEventListener(EmailService emailService, AccountRepository accountRepository) {
        this.emailService = emailService;
    }
    @Transactional
    @KafkaListener(topics = "accounts", groupId = "ledger-notification-group")
    public void handleAccountCreatedEvent(AccountCreatedEvent event) {
        try {
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
