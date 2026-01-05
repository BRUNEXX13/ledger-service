package com.bss.application.controller.transfer.mapper;

import com.bss.application.dto.request.transfer.TransferRequest;
import com.bss.domain.transfer.Transfer;
import org.springframework.stereotype.Component;

@Component
public class TransferMapper {

    public Transfer toDomain(TransferRequest request) {
        if (request == null) {
            return null;
        }
        return new Transfer(
                request.getSenderAccountId(),
                request.getReceiverAccountId(),
                request.getAmount(),
                request.getIdempotencyKey()
        );
    }
}
