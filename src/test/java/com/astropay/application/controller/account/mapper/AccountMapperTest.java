package com.astropay.application.controller.account.mapper;

import com.astropay.application.dto.response.account.AccountResponse;
import com.astropay.domain.model.account.Account;
import com.astropay.domain.model.account.AccountStatus;
import com.astropay.domain.model.user.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AccountMapperTest {

    private final AccountMapper mapper = new AccountMapper();

    @Test
    @DisplayName("Should map Account entity to AccountResponse DTO")
    void shouldMapEntityToDto() {
        // Arrange
        User user = mock(User.class);
        when(user.getId()).thenReturn(1L);

        Account account = mock(Account.class);
        when(account.getId()).thenReturn(100L);
        when(account.getUser()).thenReturn(user);
        when(account.getBalance()).thenReturn(new BigDecimal("123.45"));
        when(account.getStatus()).thenReturn(AccountStatus.ACTIVE);
        when(account.getCreatedAt()).thenReturn(Instant.now().minus(1, ChronoUnit.DAYS));
        when(account.getUpdatedAt()).thenReturn(Instant.now());

        // Act
        AccountResponse response = mapper.toAccountResponse(account);

        // Assert
        assertNotNull(response);
        assertEquals(100L, response.getId());
        assertEquals(1L, response.getUserId());
        assertEquals(0, new BigDecimal("123.45").compareTo(response.getBalance()));
        assertEquals(AccountStatus.ACTIVE, response.getStatus()); // Corrigido para usar Enum
    }
}
