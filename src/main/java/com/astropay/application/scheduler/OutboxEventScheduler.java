package com.astropay.application.scheduler;

import com.astropay.application.event.transactions.TransactionEvent;
import com.astropay.application.service.kafka.producer.KafkaProducerService;
import com.astropay.domain.model.outbox.OutboxEvent;
import com.astropay.domain.model.outbox.OutboxEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
public class OutboxEventScheduler {

    private static final Logger log = LoggerFactory.getLogger(OutboxEventScheduler.class);

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaProducerService kafkaProducerService;
    private final ObjectMapper objectMapper;

    public OutboxEventScheduler(OutboxEventRepository outboxEventRepository,
                                KafkaProducerService kafkaProducerService,
                                ObjectMapper objectMapper) {
        this.outboxEventRepository = outboxEventRepository;
        this.kafkaProducerService = kafkaProducerService;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedDelay = 5000) // Executa a cada 5 segundos
    @Transactional
    public void processOutboxEvents() {
        List<OutboxEvent> events = outboxEventRepository.findTop100ByOrderByCreatedAtAsc();
        if (events.isEmpty()) {
            return;
        }

        log.info("Found {} events in outbox to process.", events.size());

        for (OutboxEvent event : events) {
            try {
                // Desserializa o payload para o objeto de evento específico
                TransactionEvent transactionEvent = objectMapper.readValue(event.getPayload(), TransactionEvent.class);

                // Envia para o Kafka
                kafkaProducerService.sendTransactionEvent(transactionEvent);

                // Se o envio for bem-sucedido, remove o evento da tabela outbox
                outboxEventRepository.delete(event);

            } catch (JsonProcessingException e) {
                log.error("Failed to deserialize event payload for outbox event id: {}. Moving to dead-letter queue or handling error.", event.getId(), e);
                // Em um cenário real, moveríamos este evento para uma "dead-letter queue" para análise manual.
                // Por simplicidade, vamos apenas deletá-lo para não bloquear o processamento.
                outboxEventRepository.delete(event);
            } catch (Exception e) {
                log.error("Failed to send event to Kafka for outbox event id: {}. Will retry later.", event.getId(), e);
                // A transação será revertida, e o evento permanecerá no banco para a próxima tentativa.
                throw e; // Lança a exceção para garantir o rollback da transação
            }
        }
    }
}
