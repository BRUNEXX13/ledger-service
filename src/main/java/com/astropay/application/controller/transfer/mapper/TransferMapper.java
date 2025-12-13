package com.astropay.application.controller.transfer.mapper;

import com.astropay.application.dto.request.transfer.TransferRequest;
import com.astropay.domain.model.transfer.Transfer;
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
