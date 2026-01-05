package com.bss.application.event.transactions;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Represents the data for a transfer request, captured when the API call is made.
 * This event is stored in the outbox and processed asynchronously.
 *
 * @param senderAccountId  The ID of the sender's account.
 * @param receiverAccountId The ID of the receiver's account.
 * @param amount           The amount to be transferred.
 * @param idempotencyKey   The unique key for this transfer operation.
 */
public record TransferRequestedEvent(
    Long senderAccountId,
    Long receiverAccountId,
    BigDecimal amount,
    UUID idempotencyKey
) {
}
