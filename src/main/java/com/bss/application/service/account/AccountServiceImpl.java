package com.bss.application.service.account;

import com.bss.application.controller.account.mapper.AccountMapper;
import com.bss.application.dto.request.account.CreateAccountRequest;
import com.bss.application.dto.request.account.UpdateAccountRequest;
import com.bss.application.dto.response.account.AccountResponse;
import com.bss.application.event.account.AccountCreatedEvent;
import com.bss.application.exception.JsonSerializationException;
import com.bss.application.exception.ResourceNotFoundException;
import com.bss.application.service.account.port.in.AccountService;
import com.bss.domain.account.Account;
import com.bss.domain.account.AccountRepository;
import com.bss.domain.outbox.OutboxEvent;
import com.bss.domain.outbox.OutboxEventRepository;
import com.bss.domain.user.User;
import com.bss.domain.user.UserRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@Transactional
public class AccountServiceImpl implements AccountService {

    private static final Logger log = LoggerFactory.getLogger(AccountServiceImpl.class);
    private static final String ACCOUNT_NOT_FOUND_ID = "Account not found with id: ";
    private static final String USER_NOT_FOUND_ID = "User not found with id: ";

    private final AccountRepository accountRepository;
    private final UserRepository userRepository;
    private final AccountMapper accountMapper;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    public AccountServiceImpl(AccountRepository accountRepository, UserRepository userRepository, AccountMapper accountMapper, OutboxEventRepository outboxEventRepository, ObjectMapper objectMapper) {
        this.accountRepository = accountRepository;
        this.userRepository = userRepository;
        this.accountMapper = accountMapper;
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public Account createAccountForUser(User user, BigDecimal initialBalance) {
        if (user == null) {
            throw new IllegalArgumentException("User cannot be null.");
        }
        if (accountRepository.findByUser_Id(user.getId()).isPresent()) {
            throw new IllegalStateException("User already has an account.");
        }
        
        Account newAccount = new Account(user, initialBalance);
        Account savedAccount = accountRepository.save(newAccount);

        // Use Outbox Pattern instead of direct Kafka call
        createOutboxEvent(savedAccount, user);

        return savedAccount;
    }

    private void createOutboxEvent(Account account, User user) {
        AccountCreatedEvent event = new AccountCreatedEvent(
            account.getId(),
            user.getId(),
            user.getName(),
            user.getEmail(),
            account.getCreatedAt()
        );

        String payload;
        try {
            payload = objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new JsonSerializationException("Failed to serialize account created event to JSON", e);
        }

        OutboxEvent outboxEvent = new OutboxEvent(
            "Account",
            account.getId().toString(),
            "AccountCreated",
            payload
        );
        outboxEventRepository.save(outboxEvent);
        log.info("Outbox event 'AccountCreated' created for account {}.", account.getId());
    }

    @Override
    public AccountResponse createAccount(CreateAccountRequest request) {
        User user = userRepository.findById(request.userId())
            .orElseThrow(() -> new ResourceNotFoundException(USER_NOT_FOUND_ID + request.userId()));

        BigDecimal initialBalance = request.initialBalance() != null ? request.initialBalance() : BigDecimal.ZERO;
        
        // Reuses the main logic and gets the saved account directly
        Account savedAccount = createAccountForUser(user, initialBalance);

        // No more redundant database calls or event dispatching here
        return accountMapper.toAccountResponse(savedAccount);
    }

    @Override
    @Transactional(readOnly = true)
    public AccountResponse findAccountById(Long id) {
        return accountRepository.findById(id)
            .map(accountMapper::toAccountResponse)
            .orElseThrow(() -> new ResourceNotFoundException(ACCOUNT_NOT_FOUND_ID + id));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AccountResponse> findAllAccounts(Pageable pageable) {
        return accountRepository.findAll(pageable)
            .map(accountMapper::toAccountResponse);
    }

    @Override
    public AccountResponse updateAccount(Long id, UpdateAccountRequest request) {
        Account account = accountRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException(ACCOUNT_NOT_FOUND_ID + id));
        
        account.adjustBalance(request.balance());

        Account updatedAccount = accountRepository.save(account);
        return accountMapper.toAccountResponse(updatedAccount);
    }

    @Override
    public void inactivateAccount(Long id) {
        Account account = accountRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException(ACCOUNT_NOT_FOUND_ID + id));
        
        account.inactivate();

        accountRepository.save(account);
    }

    @Override
    public void deleteAccount(Long id) {
        Account account = accountRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException(ACCOUNT_NOT_FOUND_ID + id));
        
        accountRepository.delete(account);
    }
}
