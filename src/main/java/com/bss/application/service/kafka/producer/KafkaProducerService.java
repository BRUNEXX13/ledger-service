package com.bss.application.service.kafka.producer;

import com.bss.application.event.account.AccountCreatedEvent;
import com.bss.application.event.transactions.TransactionEvent;
import com.bss.application.event.transactions.TransferRequestedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

@Service
public class KafkaProducerService {

    private static final Logger log = LoggerFactory.getLogger(KafkaProducerService.class);

    private static final String TOPIC_TRANSACTIONS = "transactions";
    private static final String TOPIC_ACCOUNTS = "accounts";
    private static final String TOPIC_TRANSFER_REQUESTS = "transfer-requests";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public KafkaProducerService(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public CompletableFuture<SendResult<String, Object>> sendTransactionEvent(TransactionEvent event) {
        return sendAndLog(
                TOPIC_TRANSACTIONS,
                event,
                e -> String.valueOf(e.getIdempotencyKey()),
                "TRANSACTION"
        );
    }

    public CompletableFuture<SendResult<String, Object>> sendAccountCreatedEvent(AccountCreatedEvent event) {
        return sendAndLog(
                TOPIC_ACCOUNTS,
                event,
                e -> String.valueOf(e.getAccountId()),
                "ACCOUNT CREATION"
        );
    }

    public CompletableFuture<SendResult<String, Object>> sendTransferRequestedEvent(TransferRequestedEvent event) {
        return sendAndLog(
                TOPIC_TRANSFER_REQUESTS,
                event,
                e -> String.valueOf(e.idempotencyKey()),
                "TRANSFER REQUEST"
        );
    }

    /**
     * Generic method to handle Kafka sending and logging boilerplate.
     *
     * @param topic       The Kafka topic to send to.
     * @param event       The event object payload.
     * @param idExtractor A function to extract a unique ID from the event for logging purposes.
     * @param eventName   A human-readable name for the event type for logging.
     * @param <T>         The type of the event.
     * @return The CompletableFuture of the send result.
     */
    private <T> CompletableFuture<SendResult<String, Object>> sendAndLog(String topic,
                                                                         T event,
                                                                         Function<T, String> idExtractor,
                                                                         String eventName) {
        CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send(topic, event);

        future.whenComplete((result, ex) -> {
            String id = idExtractor.apply(event);
            if (ex != null) {
                log.error("Failed to send {} event to Kafka. Id: {}. Cause: {}", 
                        eventName, id, ex.getMessage());
            } else {
                log.info("{} event sent successfully to Kafka. Id: {}, Offset: {}", 
                        eventName, id, result.getRecordMetadata().offset());
            }
        });

        return future;
    }
}
