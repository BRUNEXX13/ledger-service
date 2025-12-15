package com.astropay.application.service.account;

import com.astropay.application.controller.account.mapper.AccountMapper;
import com.astropay.application.dto.request.account.CreateAccountRequest;
import com.astropay.application.dto.request.account.UpdateAccountRequest;
import com.astropay.application.dto.response.account.AccountResponse;
import com.astropay.application.event.account.AccountCreatedEvent;
import com.astropay.application.exception.ResourceNotFoundException;
import com.astropay.application.service.account.port.in.AccountService;
import com.astropay.application.service.kafka.producer.KafkaProducerService;
import com.astropay.domain.model.account.Account;
import com.astropay.domain.model.account.AccountRepository;
import com.astropay.domain.model.user.User;
import com.astropay.domain.model.user.UserRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@Transactional
public class AccountServiceImpl implements AccountService {

    private static final String ACCOUNT_NOT_FOUND_ID = "Account not found with id: ";
    private static final String USER_NOT_FOUND_ID = "User not found with id: ";

    private final AccountRepository accountRepository;
    private final UserRepository userRepository;
    private final AccountMapper accountMapper;
    private final KafkaProducerService kafkaProducerService;

    public AccountServiceImpl(AccountRepository accountRepository, UserRepository userRepository, AccountMapper accountMapper, KafkaProducerService kafkaProducerService) {
        this.accountRepository = accountRepository;
        this.userRepository = userRepository;
        this.accountMapper = accountMapper;
        this.kafkaProducerService = kafkaProducerService;
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

        // Centralized event dispatching
        AccountCreatedEvent event = new AccountCreatedEvent(
            savedAccount.getId(),
            user.getId(),
            user.getName(),
            user.getEmail(),
            savedAccount.getCreatedAt()
        );
        kafkaProducerService.sendAccountCreatedEvent(event);

        return savedAccount;
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
    @Cacheable(value = "accounts", key = "#id")
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
    @CachePut(value = "accounts", key = "#id")
    public AccountResponse updateAccount(Long id, UpdateAccountRequest request) {
        Account account = accountRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException(ACCOUNT_NOT_FOUND_ID + id));
        
        account.adjustBalance(request.balance());

        Account updatedAccount = accountRepository.save(account);
        return accountMapper.toAccountResponse(updatedAccount);
    }

    @Override
    @Caching(
        evict = {
            @CacheEvict(value = "accounts", key = "#id"),
            @CacheEvict(value = "accounts", key = "'user:' + #result.user.id", condition = "#result.user != null")
        }
    )
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
