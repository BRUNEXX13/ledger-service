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

    public CompletableFuture<SendResult<String, Object>> sendTransactionEvent(TransactionEvent event) {
        CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send("transactions", event);
        logSendResult("TRANSACTION", event.getIdempotencyKey().toString(), future);
        return future;
    }

    public CompletableFuture<SendResult<String, Object>> sendAccountCreatedEvent(AccountCreatedEvent event) {
        CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send("accounts", event);
        logSendResult("ACCOUNT CREATION", event.getAccountId().toString(), future);
        return future;
    }

    private void logSendResult(String eventType, String eventKey, CompletableFuture<SendResult<String, Object>> future) {
        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Failed to send {} event to Kafka. Key: {}. Cause: {}",
                        eventType, eventKey, ex.getMessage());
            } else {
                log.info("{} event sent successfully to Kafka. Key: {}, Offset: {}",
                        eventType, eventKey, result.getRecordMetadata().offset());
            }
        });
    }
}
