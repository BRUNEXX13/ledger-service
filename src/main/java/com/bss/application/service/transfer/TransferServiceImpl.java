package com.bss.application.service.transfer;

import com.bss.application.event.transactions.TransferRequestedEvent;
import com.bss.domain.transaction.Transaction;
import com.bss.domain.transfer.Transfer;
import org.springframework.stereotype.Service;

@Service
public class TransferServiceImpl implements TransferService {

    private final TransferBufferService transferBufferService;

    public TransferServiceImpl(TransferBufferService transferBufferService) {
        this.transferBufferService = transferBufferService;
    }

    @Override
    // @Transactional removed to avoid DB connection acquisition on HTTP thread
    public Transaction transfer(Transfer transfer) {
        if (transfer.getSenderAccountId().equals(transfer.getReceiverAccountId())) {
            throw new IllegalArgumentException("Sender and receiver accounts cannot be the same.");
        }

        // Just push to memory buffer. Super fast.
        TransferRequestedEvent event = new TransferRequestedEvent(
                transfer.getSenderAccountId(),
                transfer.getReceiverAccountId(),
                transfer.getAmount(),
                transfer.getIdempotencyKey()
        );
        
        transferBufferService.enqueue(event);
        // Return null as before (202 Accepted handled by Controller)
        return null;
    }
}
