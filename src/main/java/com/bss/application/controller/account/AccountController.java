package com.bss.application.controller.account;

import com.bss.application.dto.request.account.UpdateAccountRequest;
import com.bss.application.dto.response.account.AccountResponse;
import com.bss.application.service.account.port.in.AccountService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.PagedModel;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

@RestController
@RequestMapping("/accounts")
public class AccountController {

    private final AccountService accountService;
    private final PagedResourcesAssembler<AccountResponse> pagedResourcesAssembler;

    public AccountController(AccountService accountService, PagedResourcesAssembler<AccountResponse> pagedResourcesAssembler) {
        this.accountService = accountService;
        this.pagedResourcesAssembler = pagedResourcesAssembler;
    }

    @GetMapping("/{id}")
    public ResponseEntity<AccountResponse> getAccountById(@PathVariable Long id) {
        AccountResponse account = accountService.findAccountById(id);
        account.add(linkTo(methodOn(AccountController.class).getAccountById(id)).withSelfRel());
        account.add(linkTo(methodOn(AccountController.class).getAllAccounts(Pageable.unpaged())).withRel("all-accounts"));
        return ResponseEntity.ok(account);
    }

    @GetMapping
    public ResponseEntity<PagedModel<EntityModel<AccountResponse>>> getAllAccounts(Pageable pageable) {
        Page<AccountResponse> accountPage = accountService.findAllAccounts(pageable);
        accountPage.forEach(acc -> acc.add(linkTo(methodOn(AccountController.class).getAccountById(acc.getId())).withSelfRel()));
        
        PagedModel<EntityModel<AccountResponse>> pagedModel = pagedResourcesAssembler.toModel(accountPage);
        return ResponseEntity.ok(pagedModel);
    }

    @PutMapping("/{id}")
    public ResponseEntity<AccountResponse> updateAccount(@PathVariable Long id, @Valid @RequestBody UpdateAccountRequest request) {
        AccountResponse updatedAccount = accountService.updateAccount(id, request);
        updatedAccount.add(linkTo(methodOn(AccountController.class).getAccountById(id)).withSelfRel());
        return ResponseEntity.ok(updatedAccount);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> inactivateAccount(@PathVariable Long id) {
        accountService.inactivateAccount(id);
        return ResponseEntity.noContent().build();
    }
}
