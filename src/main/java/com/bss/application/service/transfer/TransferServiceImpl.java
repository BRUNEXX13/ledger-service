package com.bss.application.service.transfer;

import com.bss.application.event.transactions.TransferRequestedEvent;
import com.bss.domain.outbox.OutboxEvent;
import com.bss.domain.outbox.OutboxEventRepository;
import com.bss.domain.transaction.Transaction;
import com.bss.domain.transfer.Transfer;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransferServiceImpl implements TransferService {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    public TransferServiceImpl(OutboxEventRepository outboxEventRepository, ObjectMapper objectMapper) {
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional // Garante que o evento seja salvo no banco de forma atômica e segura
    public Transaction transfer(Transfer transfer) {
        if (transfer.getSenderAccountId().equals(transfer.getReceiverAccountId())) {
            throw new IllegalArgumentException("Sender and receiver accounts cannot be the same.");
        }

        TransferRequestedEvent event = new TransferRequestedEvent(
                transfer.getSenderAccountId(),
                transfer.getReceiverAccountId(),
                transfer.getAmount(),
                transfer.getIdempotencyKey()
        );

        try {
            String payload = objectMapper.writeValueAsString(event);
            
            OutboxEvent outboxEvent = new OutboxEvent(
                    "Transfer",
                    transfer.getIdempotencyKey().toString(),
                    "TransferRequested",
                    payload
            );
            
            // Gravação síncrona no banco de dados (Segurança Máxima)
            outboxEventRepository.save(outboxEvent);
            
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Error serializing transfer event", e);
        }

        // Retorna null pois o processamento é assíncrono (202 Accepted)
        return null;
    }
}
