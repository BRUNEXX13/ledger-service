package com.bss.application.controller.account.mapper;

import com.bss.application.dto.response.account.AccountResponse;
import com.bss.domain.account.Account;
import com.bss.domain.account.AccountStatus;
import com.bss.domain.user.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AccountMapperTest {

    private final AccountMapper mapper = new AccountMapper();

    @Test
    @DisplayName("Should map Account to AccountResponse")
    void shouldMapAccountToResponse() {
        // Arrange
        User user = mock(User.class);
        when(user.getId()).thenReturn(10L);

        Account account = new Account(user, new BigDecimal("500.00"));
        ReflectionTestUtils.setField(account, "id", 1L);
        
        LocalDateTime now = LocalDateTime.now();
        ReflectionTestUtils.setField(account, "createdAt", now);
        ReflectionTestUtils.setField(account, "updatedAt", now);

        // Act
        AccountResponse response = mapper.toAccountResponse(account);

        // Assert
        assertNotNull(response);
        assertEquals(1L, response.getId());
        assertEquals(10L, response.getUserId());
        assertEquals(new BigDecimal("500.00"), response.getBalance());
        assertEquals(AccountStatus.ACTIVE, response.getStatus());
        assertEquals(now, response.getCreatedAt());
        assertEquals(now, response.getUpdatedAt());
    }
}
