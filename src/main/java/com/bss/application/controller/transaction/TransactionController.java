package com.bss.application.controller.transaction;

import com.bss.application.dto.response.transaction.TransactionResponse;
import com.bss.application.dto.response.transaction.TransactionUserResponse;
import com.bss.application.service.transaction.TransactionAuditService;
import com.bss.application.service.transaction.port.in.TransactionService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.web.SlicedResourcesAssembler;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.SlicedModel;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

@RestController
@RequestMapping("/transactions")
public class TransactionController {

    private final TransactionService transactionService;
    private final TransactionAuditService transactionAuditService;
    private final SlicedResourcesAssembler<TransactionResponse> slicedResourcesAssembler;

    public TransactionController(TransactionService transactionService, TransactionAuditService transactionAuditService, SlicedResourcesAssembler<TransactionResponse> slicedResourcesAssembler) {
        this.transactionService = transactionService;
        this.transactionAuditService = transactionAuditService;
        this.slicedResourcesAssembler = slicedResourcesAssembler;
    }

    @GetMapping
    public ResponseEntity<SlicedModel<EntityModel<TransactionResponse>>> getAllTransactions(Pageable pageable) {
        Slice<TransactionResponse> transactionSlice = transactionService.findAllTransactions(pageable);
        
        transactionSlice.forEach(tx -> tx.add(linkTo(methodOn(TransactionController.class).getTransactionById(tx.getId())).withSelfRel()));
        
        SlicedModel<EntityModel<TransactionResponse>> slicedModel = slicedResourcesAssembler.toModel(transactionSlice);
        
        return ResponseEntity.ok(slicedModel);
    }

    @GetMapping("/{id}")
    public ResponseEntity<TransactionResponse> getTransactionById(@PathVariable Long id) {
        TransactionResponse transaction = transactionService.findTransactionById(id);
        
        transaction.add(linkTo(methodOn(TransactionController.class).getTransactionById(id)).withSelfRel());
        transaction.add(linkTo(methodOn(TransactionController.class).getAllTransactions(Pageable.unpaged())).withRel("all-transactions"));

        return ResponseEntity.ok(transaction);
    }

    @GetMapping("/{id}/sender")
    public ResponseEntity<TransactionUserResponse> getTransactionSender(@PathVariable Long id) {
        TransactionUserResponse response = transactionAuditService.findUserByTransactionId(id);
        return ResponseEntity.ok(response);
    }
}
