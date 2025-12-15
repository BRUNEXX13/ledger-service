package com.astropay.application.service.account;

import com.astropay.application.controller.account.mapper.AccountMapper;
import com.astropay.application.dto.request.account.CreateAccountRequest;
import com.astropay.application.dto.response.account.AccountResponse;
import com.astropay.application.exception.ResourceNotFoundException;
import com.astropay.application.service.kafka.producer.KafkaProducerService;
import com.astropay.domain.model.account.Account;
import com.astropay.domain.model.account.AccountRepository;
import com.astropay.domain.model.account.AccountStatus;
import com.astropay.domain.model.user.Role;
import com.astropay.domain.model.user.User;
import com.astropay.domain.model.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
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
    private KafkaProducerService kafkaProducerService;

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
    @DisplayName("createAccountForUser should create and save a new account")
    void createAccountForUser_shouldCreateAndSaveAccount() {
        // Arrange
        when(accountRepository.findByUser_Id(user.getId())).thenReturn(Optional.empty());

        // Act
        accountService.createAccountForUser(user, BigDecimal.TEN);

        // Assert
        verify(accountRepository).save(any(Account.class));
    }

    @Test
    @DisplayName("createAccountForUser should throw exception if user is null")
    void createAccountForUser_shouldThrowExceptionIfUserIsNull() {
        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            accountService.createAccountForUser(null, BigDecimal.TEN);
        });
        assertEquals("User cannot be null.", exception.getMessage());
    }

    @Test
    @DisplayName("createAccountForUser should throw exception if account already exists")
    void createAccountForUser_shouldThrowExceptionIfAccountExists() {
        // Arrange
        when(accountRepository.findByUser_Id(user.getId())).thenReturn(Optional.of(new Account(user, BigDecimal.ZERO)));

        // Act & Assert
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            accountService.createAccountForUser(user, BigDecimal.TEN);
        });
        assertEquals("User already has an account.", exception.getMessage());
    }

    // Tests for createAccount
    @Test
    @DisplayName("createAccount should create account, send event, and return response")
    void createAccount_shouldWorkSuccessfully() {
        // Arrange
        CreateAccountRequest request = new CreateAccountRequest(user.getId(), BigDecimal.TEN);
        Account newAccount = new Account(user, BigDecimal.TEN);
        AccountResponse response = new AccountResponse(1L, user.getId(), BigDecimal.TEN, AccountStatus.ACTIVE, LocalDateTime.now(), LocalDateTime.now());

        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(accountRepository.findByUser_Id(user.getId()))
            .thenReturn(Optional.empty()) // First call inside createAccountForUser
            .thenReturn(Optional.of(newAccount)); // Second call to get the saved account
        when(accountMapper.toAccountResponse(newAccount)).thenReturn(response);

        // Act
        accountService.createAccount(request);

        // Assert
        verify(accountRepository).save(any(Account.class));
        verify(kafkaProducerService).sendAccountCreatedEvent(any());
        verify(accountMapper).toAccountResponse(newAccount);
    }

    @Test
    @DisplayName("createAccount should throw exception if user not found")
    void createAccount_shouldThrowExceptionIfUserNotFound() {
        // Arrange
        CreateAccountRequest request = new CreateAccountRequest(99L, BigDecimal.TEN);
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(ResourceNotFoundException.class, () -> {
            accountService.createAccount(request);
        });
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
        assertThrows(ResourceNotFoundException.class, () -> {
            accountService.findAccountById(1L);
        });
    }
}
