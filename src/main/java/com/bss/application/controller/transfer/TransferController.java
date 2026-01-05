package com.bss.application.controller.transfer;

import com.bss.application.controller.transfer.mapper.TransferMapper;
import com.bss.application.dto.request.transfer.TransferRequest;
import com.bss.application.service.transfer.TransferService;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/transfers")
public class TransferController {

    private final TransferService transferService;
    private final TransferMapper transferMapper;

    public TransferController(TransferService transferService, TransferMapper transferMapper) {
        this.transferService = transferService;
        this.transferMapper = transferMapper;
    }

    @PostMapping
    @RateLimiter(name = "transfers")
    public ResponseEntity<Void> transfer(@Valid @RequestBody TransferRequest request) {
        var transfer = transferMapper.toDomain(request);
        transferService.transfer(transfer);
        return ResponseEntity.accepted().build();
    }
}
