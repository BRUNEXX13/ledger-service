package com.bss.application.service.account;

import com.bss.application.controller.account.mapper.AccountMapper;
import com.bss.application.dto.request.account.CreateAccountRequest;
import com.bss.application.dto.request.account.UpdateAccountRequest;
import com.bss.application.dto.response.account.AccountResponse;
import com.bss.application.exception.ResourceNotFoundException;
import com.bss.domain.account.Account;
import com.bss.domain.account.AccountRepository;
import com.bss.domain.account.AccountStatus;
import com.bss.domain.outbox.OutboxEvent;
import com.bss.domain.outbox.OutboxEventRepository;
import com.bss.domain.user.Role;
import com.bss.domain.user.User;
import com.bss.domain.user.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountServiceImplTest {

    @Mock
    private AccountRepository accountRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private AccountMapper accountMapper;
    @Mock
    private OutboxEventRepository outboxEventRepository;
    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private AccountServiceImpl accountService;

    private User user;

    @BeforeEach
    void setUp() {
        user = new User("Test User", "123456", "test@test.com", Role.ROLE_EMPLOYEE);
        ReflectionTestUtils.setField(user, "id", 1L);
    }

    // Tests for createAccountForUser
    @Test
    @DisplayName("createAccountForUser should create account, save outbox event, and return account")
    void createAccountForUser_shouldCreateAndSaveAccount() {
        // Arrange
        Account savedAccount = new Account(user, BigDecimal.TEN);
        ReflectionTestUtils.setField(savedAccount, "id", 100L); // Ensure ID is set for OutboxEvent creation
        
        when(accountRepository.findByUser_Id(user.getId())).thenReturn(Optional.empty());
        when(accountRepository.save(any(Account.class))).thenReturn(savedAccount);

        // Act
        Account result = accountService.createAccountForUser(user, BigDecimal.TEN);

        // Assert
        assertNotNull(result);
        verify(accountRepository).save(any(Account.class));
        
        // Verify Outbox Event is saved instead of direct Kafka call
        verify(outboxEventRepository).save(any(OutboxEvent.class));
    }

    @Test
    @DisplayName("createAccountForUser should throw exception if user is null")
    void createAccountForUser_shouldThrowExceptionIfUserIsNull() {
        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> 
            accountService.createAccountForUser(null, BigDecimal.TEN)
        );
        assertEquals("User cannot be null.", exception.getMessage());
    }

    @Test
    @DisplayName("createAccountForUser should throw exception if account already exists")
    void createAccountForUser_shouldThrowExceptionIfAccountExists() {
        // Arrange
        when(accountRepository.findByUser_Id(user.getId())).thenReturn(Optional.of(new Account(user, BigDecimal.ZERO)));

        // Act & Assert
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> 
            accountService.createAccountForUser(user, BigDecimal.TEN)
        );
        assertEquals("User already has an account.", exception.getMessage());
    }

    // Tests for createAccount
    @Test
    @DisplayName("createAccount should call createAccountForUser and return response")
    void createAccount_shouldWorkSuccessfully() {
        // Arrange
        CreateAccountRequest request = new CreateAccountRequest(user.getId(), BigDecimal.TEN);
        Account savedAccount = new Account(user, BigDecimal.TEN);
        ReflectionTestUtils.setField(savedAccount, "id", 1L); // Set ID for the saved object
        AccountResponse expectedResponse = new AccountResponse(1L, user.getId(), BigDecimal.TEN, AccountStatus.ACTIVE, LocalDateTime.now(), LocalDateTime.now());

        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(accountRepository.findByUser_Id(user.getId())).thenReturn(Optional.empty());
        when(accountRepository.save(any(Account.class))).thenReturn(savedAccount);
        when(accountMapper.toAccountResponse(savedAccount)).thenReturn(expectedResponse);

        // Act
        AccountResponse actualResponse = accountService.createAccount(request);

        // Assert
        assertNotNull(actualResponse);
        verify(accountRepository).save(any(Account.class));
        verify(outboxEventRepository).save(any(OutboxEvent.class)); // Verify Outbox
        verify(accountMapper).toAccountResponse(savedAccount);
    }

    @Test
    @DisplayName("createAccount should throw exception if user not found")
    void createAccount_shouldThrowExceptionIfUserNotFound() {
        // Arrange
        CreateAccountRequest request = new CreateAccountRequest(99L, BigDecimal.TEN);
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(ResourceNotFoundException.class, () -> 
            accountService.createAccount(request)
        );
    }
    
    // Tests for findAccountById
    @Test
    @DisplayName("findAccountById should return account when found")
    void findAccountById_shouldReturnAccount() {
        // Arrange
        Account account = new Account(user, BigDecimal.TEN);
        AccountResponse response = new AccountResponse(1L, user.getId(), BigDecimal.TEN, AccountStatus.ACTIVE, LocalDateTime.now(), LocalDateTime.now());
        
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));
        when(accountMapper.toAccountResponse(account)).thenReturn(response);

        // Act
        AccountResponse result = accountService.findAccountById(1L);

        // Assert
        assertNotNull(result);
        verify(accountMapper).toAccountResponse(account);
    }

    @Test
    @DisplayName("findAccountById should throw exception when not found")
    void findAccountById_shouldThrowExceptionWhenNotFound() {
        // Arrange
        when(accountRepository.findById(1L)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(ResourceNotFoundException.class, () -> 
            accountService.findAccountById(1L)
        );
    }

    // Test for findAllAccounts
    @Test
    @DisplayName("findAllAccounts should return a page of account responses")
    void findAllAccounts_shouldReturnPageOfAccountResponses() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 10);
        Account account = new Account(user, BigDecimal.TEN);
        Page<Account> accountPage = new PageImpl<>(Collections.singletonList(account), pageable, 1);
        AccountResponse response = new AccountResponse(1L, user.getId(), BigDecimal.TEN, AccountStatus.ACTIVE, LocalDateTime.now(), LocalDateTime.now());

        when(accountRepository.findAll(pageable)).thenReturn(accountPage);
        when(accountMapper.toAccountResponse(any(Account.class))).thenReturn(response);

        // Act
        Page<AccountResponse> result = accountService.findAllAccounts(pageable);

        // Assert
        assertFalse(result.isEmpty());
        assertEquals(1, result.getTotalElements());
        verify(accountRepository).findAll(pageable);
        verify(accountMapper).toAccountResponse(account);
    }

    // Tests for updateAccount
    @Test
    @DisplayName("updateAccount should update balance and return response")
    void updateAccount_shouldUpdateBalanceAndReturnResponse() {
        // Arrange
        Long accountId = 1L;
        BigDecimal newBalance = new BigDecimal("500.00");
        UpdateAccountRequest request = new UpdateAccountRequest(newBalance);
        
        Account originalAccount = new Account(user, new BigDecimal("100.00"));
        Account spyAccount = spy(originalAccount); // Spy to verify method calls on the object

        AccountResponse response = new AccountResponse(accountId, user.getId(), newBalance, AccountStatus.ACTIVE, LocalDateTime.now(), LocalDateTime.now());

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(spyAccount));
        when(accountRepository.save(any(Account.class))).thenReturn(spyAccount);
        when(accountMapper.toAccountResponse(spyAccount)).thenReturn(response);

        // Act
        AccountResponse result = accountService.updateAccount(accountId, request);

        // Assert
        assertNotNull(result);
        verify(spyAccount).adjustBalance(newBalance); // Verify the business method was called
        verify(accountRepository).save(spyAccount);
        assertEquals(newBalance, result.getBalance());
    }

    @Test
    @DisplayName("updateAccount should throw exception when account not found")
    void updateAccount_shouldThrowExceptionWhenNotFound() {
        // Arrange
        Long accountId = 99L;
        UpdateAccountRequest request = new UpdateAccountRequest(BigDecimal.TEN);
        when(accountRepository.findById(accountId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(ResourceNotFoundException.class, () -> 
            accountService.updateAccount(accountId, request)
        );
    }

    // Tests for inactivateAccount
    @Test
    @DisplayName("inactivateAccount should set status to INACTIVE")
    void inactivateAccount_shouldSetStatusToInactive() {
        // Arrange
        Long accountId = 1L;
        // Balance must be zero to inactivate
        Account account = new Account(user, BigDecimal.ZERO);
        Account spyAccount = spy(account);

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(spyAccount));

        // Act
        accountService.inactivateAccount(accountId);

        // Assert
        verify(spyAccount).inactivate(); // Verify the business method was called
        verify(accountRepository).save(spyAccount);
    }

    @Test
    @DisplayName("inactivateAccount should throw exception when account not found")
    void inactivateAccount_shouldThrowExceptionWhenNotFound() {
        // Arrange
        Long accountId = 99L;
        when(accountRepository.findById(accountId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(ResourceNotFoundException.class, () -> 
            accountService.inactivateAccount(accountId)
        );
    }
}
