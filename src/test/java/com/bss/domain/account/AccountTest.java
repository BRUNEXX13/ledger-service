package com.bss.domain.account;

import com.bss.domain.user.Role;
import com.bss.domain.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class AccountTest {

    private User user;

    @BeforeEach
    void setUp() {
        user = new User("Test User", "12345678900", "test@example.com", Role.ROLE_EMPLOYEE);
    }

    @Test
    @DisplayName("Should create active account with initial balance")
    void shouldCreateActiveAccount() {
        BigDecimal initialBalance = new BigDecimal("100.00");
        Account account = new Account(user, initialBalance);

        assertEquals(user, account.getUser());
        assertEquals(initialBalance, account.getBalance());
        assertEquals(AccountStatus.ACTIVE, account.getStatus());
    }

    @Test
    @DisplayName("Should deposit positive amount successfully")
    void shouldDepositPositiveAmount() {
        Account account = new Account(user, new BigDecimal("100.00"));
        account.deposit(new BigDecimal("50.00"));

        assertEquals(new BigDecimal("150.00"), account.getBalance());
    }

    @Test
    @DisplayName("Should throw exception when depositing negative amount")
    void shouldThrowExceptionWhenDepositingNegativeAmount() {
        Account account = new Account(user, new BigDecimal("100.00"));
        
        assertThrows(IllegalArgumentException.class, () -> account.deposit(new BigDecimal("-10.00")));
    }

    @Test
    @DisplayName("Should throw exception when depositing null amount")
    void shouldThrowExceptionWhenDepositingNullAmount() {
        Account account = new Account(user, new BigDecimal("100.00"));
        
        assertThrows(IllegalArgumentException.class, () -> account.deposit(null));
    }

    @Test
    @DisplayName("Should throw exception when depositing to inactive account")
    void shouldThrowExceptionWhenDepositingToInactiveAccount() {
        Account account = new Account(user, BigDecimal.ZERO);
        account.inactivate();
        
        assertThrows(IllegalStateException.class, () -> account.deposit(BigDecimal.TEN));
    }

    @Test
    @DisplayName("Should withdraw valid amount successfully")
    void shouldWithdrawValidAmount() {
        Account account = new Account(user, new BigDecimal("100.00"));
        account.withdraw(new BigDecimal("40.00"));

        assertEquals(new BigDecimal("60.00"), account.getBalance());
    }

    @Test
    @DisplayName("Should throw exception when withdrawing more than balance")
    void shouldThrowExceptionWhenWithdrawingInsufficientBalance() {
        Account account = new Account(user, new BigDecimal("100.00"));
        
        assertThrows(InsufficientBalanceException.class, () -> account.withdraw(new BigDecimal("150.00")));
    }

    @Test
    @DisplayName("Should throw exception when withdrawing negative amount")
    void shouldThrowExceptionWhenWithdrawingNegativeAmount() {
        Account account = new Account(user, new BigDecimal("100.00"));
        
        assertThrows(IllegalArgumentException.class, () -> account.withdraw(new BigDecimal("-10.00")));
    }

    @Test
    @DisplayName("Should throw exception when withdrawing from blocked account")
    void shouldThrowExceptionWhenWithdrawingFromBlockedAccount() {
        Account account = new Account(user, new BigDecimal("100.00"));
        account.block();
        
        assertThrows(IllegalStateException.class, () -> account.withdraw(BigDecimal.TEN));
    }

    @Test
    @DisplayName("Should adjust balance successfully")
    void shouldAdjustBalance() {
        Account account = new Account(user, new BigDecimal("100.00"));
        account.adjustBalance(new BigDecimal("200.00"));

        assertEquals(new BigDecimal("200.00"), account.getBalance());
    }

    @Test
    @DisplayName("Should throw exception when adjusting balance to negative")
    void shouldThrowExceptionWhenAdjustingBalanceToNegative() {
        Account account = new Account(user, new BigDecimal("100.00"));
        
        assertThrows(IllegalArgumentException.class, () -> account.adjustBalance(new BigDecimal("-1.00")));
    }

    @Test
    @DisplayName("Should inactivate account with zero balance")
    void shouldInactivateAccountWithZeroBalance() {
        Account account = new Account(user, BigDecimal.ZERO);
        account.inactivate();

        assertEquals(AccountStatus.INACTIVE, account.getStatus());
    }

    @Test
    @DisplayName("Should throw exception when inactivating account with positive balance")
    void shouldThrowExceptionWhenInactivatingAccountWithBalance() {
        Account account = new Account(user, BigDecimal.TEN);
        
        assertThrows(IllegalStateException.class, () -> account.inactivate());
    }

    @Test
    @DisplayName("Should activate account")
    void shouldActivateAccount() {
        Account account = new Account(user, BigDecimal.ZERO);
        account.inactivate();
        assertEquals(AccountStatus.INACTIVE, account.getStatus());

        account.activate();
        assertEquals(AccountStatus.ACTIVE, account.getStatus());
    }

    @Test
    @DisplayName("Should block account")
    void shouldBlockAccount() {
        Account account = new Account(user, BigDecimal.TEN);
        account.block();

        assertEquals(AccountStatus.BLOCKED, account.getStatus());
    }
}
