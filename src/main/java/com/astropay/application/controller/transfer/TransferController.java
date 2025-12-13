package com.astropay.application.controller.transfer;

import com.astropay.application.controller.transfer.mapper.TransferMapper;
import com.astropay.application.dto.request.transfer.TransferRequest;
import com.astropay.application.service.transfer.TransferService;
import com.astropay.domain.model.account.InsufficientBalanceException;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/transfers")
public class TransferController {

    private static final Logger log = LoggerFactory.getLogger(TransferController.class);

    private final TransferService transferService;
    private final TransferMapper transferMapper;

    public TransferController(TransferService transferService, TransferMapper transferMapper) {
        this.transferService = transferService;
        this.transferMapper = transferMapper;
    }

    @PostMapping
    @RateLimiter(name = "transfers") // Aplica o rate limiter específico para transferências
    public ResponseEntity<?> transfer(@Valid @RequestBody TransferRequest request) {
        try {
            var transfer = transferMapper.toDomain(request);
            transferService.transfer(transfer);
            return ResponseEntity.accepted().build();

        } catch (InsufficientBalanceException e) {
            log.warn("Transfer rejected due to insufficient balance for sender account {}: {}", request.getSenderAccountId(), e.getMessage());
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            log.warn("Transfer rejected due to invalid argument: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("An unexpected error occurred during transfer for idempotency key {}", request.getIdempotencyKey(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "An internal error occurred. Please try again later."));
        }
    }
}
