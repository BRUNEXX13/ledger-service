package com.bss.application.service.kafka.producer;

import com.bss.application.event.account.AccountCreatedEvent;
import com.bss.application.event.transactions.TransactionEvent;
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
                log.error("Failed to send TRANSACTION event to Kafka. IdempotencyKey: {}. Cause: {}",
                        event.getIdempotencyKey(), ex.getMessage());
            } else {
                log.info("TRANSACTION event sent successfully to Kafka. IdempotencyKey: {}, Offset: {}",
                        event.getIdempotencyKey(), result.getRecordMetadata().offset());
            }
        });
    }

    public void sendAccountCreatedEvent(AccountCreatedEvent event) {
        CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send("accounts", event);

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Failed to send ACCOUNT CREATION event to Kafka. AccountId: {}. Cause: {}",
                        event.getAccountId(), ex.getMessage());
            } else {
                log.info("ACCOUNT CREATION event sent successfully to Kafka. AccountId: {}, Offset: {}",
                        event.getAccountId(), result.getRecordMetadata().offset());
            }
        });
    }
}
