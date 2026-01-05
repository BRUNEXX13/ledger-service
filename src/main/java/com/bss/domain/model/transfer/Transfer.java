package com.bss.domain.model.transfer;

import java.math.BigDecimal;
import java.util.UUID;

public class Transfer {

    private final Long senderAccountId;
    private final Long receiverAccountId;
    private final BigDecimal amount;
    private final UUID idempotencyKey;

    public Transfer(Long senderAccountId, Long receiverAccountId, BigDecimal amount, UUID idempotencyKey) {
        this.senderAccountId = senderAccountId;
        this.receiverAccountId = receiverAccountId;
        this.amount = amount;
        this.idempotencyKey = idempotencyKey;
    }

    public Long getSenderAccountId() {
        return senderAccountId;
    }

    public Long getReceiverAccountId() {
        return receiverAccountId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public UUID getIdempotencyKey() {
        return idempotencyKey;
    }
}
