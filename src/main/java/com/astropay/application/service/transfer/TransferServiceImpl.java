package com.astropay.application.service.transfer;

import com.astropay.application.event.transactions.TransferRequestedEvent;
import com.astropay.application.exception.JsonSerializationException;
import com.astropay.domain.model.outbox.OutboxEvent;
import com.astropay.domain.model.outbox.OutboxEventRepository;
import com.astropay.domain.model.transaction.Transaction;
import com.astropay.domain.model.transfer.Transfer;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransferServiceImpl implements TransferService {

    private static final Logger log = LoggerFactory.getLogger(TransferServiceImpl.class);

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    public TransferServiceImpl(OutboxEventRepository outboxEventRepository, ObjectMapper objectMapper) {
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public Transaction transfer(Transfer transfer) {
        if (transfer.getSenderAccountId().equals(transfer.getReceiverAccountId())) {
            throw new IllegalArgumentException("Sender and receiver accounts cannot be the same.");
        }

        // The only responsibility is to create the event for asynchronous processing.
        // No database reads are performed here.
        createOutboxEvent(transfer);

        // Return null or a temporary object, as the transaction has not yet been created.
        // Ideally, the API should not return a body, just 202-Accepted.
        return null;
    }

    private void createOutboxEvent(Transfer transfer) {
        TransferRequestedEvent event = new TransferRequestedEvent(
                transfer.getSenderAccountId(),
                transfer.getReceiverAccountId(),
                transfer.getAmount(),
                transfer.getIdempotencyKey()
        );

        String payload;
        try {
            payload = objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new JsonSerializationException("Failed to serialize transfer request event to JSON", e);
        }

        OutboxEvent outboxEvent = new OutboxEvent(
                "Transfer", // AggregateType
                transfer.getIdempotencyKey().toString(), // AggregateId
                "TransferRequested", // EventType
                payload
        );
        outboxEventRepository.save(outboxEvent);
        log.info("Outbox event 'TransferRequested' created for idempotency key {}.", transfer.getIdempotencyKey());
    }
}
