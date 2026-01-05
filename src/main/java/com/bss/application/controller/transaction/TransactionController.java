package com.bss.application.controller.transaction;

import com.bss.application.dto.response.transaction.TransactionResponse;
import com.bss.application.dto.response.transaction.TransactionUserResponse;
import com.bss.application.service.transaction.TransactionAuditService;
import com.bss.application.service.transaction.port.in.TransactionService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.PagedModel;
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
    private final PagedResourcesAssembler<TransactionResponse> pagedResourcesAssembler;

    public TransactionController(TransactionService transactionService, TransactionAuditService transactionAuditService, PagedResourcesAssembler<TransactionResponse> pagedResourcesAssembler) {
        this.transactionService = transactionService;
        this.transactionAuditService = transactionAuditService;
        this.pagedResourcesAssembler = pagedResourcesAssembler;
    }

    @GetMapping
    public ResponseEntity<PagedModel<EntityModel<TransactionResponse>>> getAllTransactions(Pageable pageable) {
        Page<TransactionResponse> transactionPage = transactionService.findAllTransactions(pageable);
        
        transactionPage.forEach(tx -> tx.add(linkTo(methodOn(TransactionController.class).getTransactionById(tx.getId())).withSelfRel()));
        
        PagedModel<EntityModel<TransactionResponse>> pagedModel = pagedResourcesAssembler.toModel(transactionPage);
        
        return ResponseEntity.ok(pagedModel);
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
