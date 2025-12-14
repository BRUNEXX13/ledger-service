package com.astropay.application.service.kafka.producer;

import com.astropay.application.event.account.AccountCreatedEvent;
import com.astropay.application.event.transactions.TransactionEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Service
public class KafkaProducerService {

    private static final Logger log = LoggerFactory.getLogger(KafkaProducerService.class);
    
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public KafkaProducerService(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void sendTransactionEvent(TransactionEvent event) {
        CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send("transactions", event);

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Falha ao enviar evento de TRANSAÇÃO para o Kafka. IdempotencyKey: {}. Causa: {}",
                        event.getIdempotencyKey(), ex.getMessage());
            } else {
                log.info("Evento de TRANSAÇÃO enviado com sucesso para o Kafka. IdempotencyKey: {}, Offset: {}",
                        event.getIdempotencyKey(), result.getRecordMetadata().offset());
            }
        });
    }

    public void sendAccountCreatedEvent(AccountCreatedEvent event) {
        CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send("accounts", event);

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Falha ao enviar evento de CRIAÇÃO DE CONTA para o Kafka. AccountId: {}. Causa: {}",
                        event.getAccountId(), ex.getMessage());
            } else {
                log.info("Evento de CRIAÇÃO DE CONTA enviado com sucesso para o Kafka. AccountId: {}, Offset: {}",
                        event.getAccountId(), result.getRecordMetadata().offset());
            }
        });
    }
}
