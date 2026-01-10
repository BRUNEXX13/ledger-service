package com.bss.domain.account;

import com.bss.domain.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class AccountTest {

    private User user;
    private Account account;

    @BeforeEach
    void setUp() {
        user = Mockito.mock(User.class);
        account = new Account(user, new BigDecimal("100.00"));
    }

    @Test
    @DisplayName("Should deposit amount successfully")
    void shouldDepositAmountSuccessfully() {
        account.deposit(new BigDecimal("50.00"));
        assertEquals(new BigDecimal("150.00"), account.getBalance());
    }

    @Test
    @DisplayName("Should throw exception when depositing negative amount")
    void shouldThrowExceptionWhenDepositingNegativeAmount() {
        assertThrows(IllegalArgumentException.class, () -> account.deposit(new BigDecimal("-10.00")));
    }

    @Test
    @DisplayName("Should throw exception when depositing to inactive account")
    void shouldThrowExceptionWhenDepositingToInactiveAccount() {
        // First, set balance to zero so we can inactivate
        account.adjustBalance(BigDecimal.ZERO);
        account.inactivate();
        
        // Now try to deposit
        assertThrows(IllegalStateException.class, () -> account.deposit(new BigDecimal("10.00")));
    }
    
    @Test
    @DisplayName("Should throw exception when depositing to blocked account")
    void shouldThrowExceptionWhenDepositingToBlockedAccount() {
        account.block();
        assertThrows(IllegalStateException.class, () -> account.deposit(new BigDecimal("10.00")));
    }

    @Test
    @DisplayName("Should withdraw amount successfully")
    void shouldWithdrawAmountSuccessfully() {
        account.withdraw(new BigDecimal("40.00"));
        assertEquals(new BigDecimal("60.00"), account.getBalance());
    }

    @Test
    @DisplayName("Should throw exception when withdrawing more than balance")
    void shouldThrowExceptionWhenWithdrawingInsufficientBalance() {
        assertThrows(InsufficientBalanceException.class, () -> account.withdraw(new BigDecimal("150.00")));
    }

    @Test
    @DisplayName("Should throw exception when withdrawing negative amount")
    void shouldThrowExceptionWhenWithdrawingNegativeAmount() {
        assertThrows(IllegalArgumentException.class, () -> account.withdraw(new BigDecimal("-10.00")));
    }

    @Test
    @DisplayName("Should throw exception when withdrawing from inactive account")
    void shouldThrowExceptionWhenWithdrawingFromInactiveAccount() {
        account.adjustBalance(BigDecimal.ZERO);
        account.inactivate();
        assertThrows(IllegalStateException.class, () -> account.withdraw(new BigDecimal("10.00")));
    }

    @Test
    @DisplayName("Should adjust balance successfully")
    void shouldAdjustBalanceSuccessfully() {
        account.adjustBalance(new BigDecimal("500.00"));
        assertEquals(new BigDecimal("500.00"), account.getBalance());
    }

    @Test
    @DisplayName("Should throw exception when adjusting balance to negative")
    void shouldThrowExceptionWhenAdjustingBalanceToNegative() {
        assertThrows(IllegalArgumentException.class, () -> account.adjustBalance(new BigDecimal("-1.00")));
    }

    @Test
    @DisplayName("Should inactivate account successfully when balance is zero")
    void shouldInactivateAccountSuccessfully() {
        account.adjustBalance(BigDecimal.ZERO);
        account.inactivate();
        assertEquals(AccountStatus.INACTIVE, account.getStatus());
    }

    @Test
    @DisplayName("Should throw exception when inactivating account with positive balance")
    void shouldThrowExceptionWhenInactivatingAccountWithBalance() {
        assertThrows(IllegalStateException.class, () -> account.inactivate());
    }

    @Test
    @DisplayName("Should block and activate account")
    void shouldBlockAndActivateAccount() {
        account.block();
        assertEquals(AccountStatus.BLOCKED, account.getStatus());

        account.activate();
        assertEquals(AccountStatus.ACTIVE, account.getStatus());
    }

    @Test
    @DisplayName("Should verify equality based on ID")
    void shouldVerifyEqualityBasedOnId() {
        Account acc1 = new Account(user, BigDecimal.TEN);
        Account acc2 = new Account(user, BigDecimal.TEN);
        Account acc3 = new Account(user, BigDecimal.TEN);

        ReflectionTestUtils.setField(acc1, "id", 1L);
        ReflectionTestUtils.setField(acc2, "id", 1L);
        ReflectionTestUtils.setField(acc3, "id", 2L);

        // Same ID -> Equal
        assertEquals(acc1, acc2);
        assertEquals(acc1.hashCode(), acc2.hashCode());

        // Different ID -> Not Equal
        assertNotEquals(acc1, acc3);
        assertNotEquals(acc1.hashCode(), acc3.hashCode());
    }

    @Test
    @DisplayName("Should not be equal to null or different class")
    void shouldNotBeEqualToNullOrDifferentClass() {
        Account acc = new Account(user, BigDecimal.TEN);
        ReflectionTestUtils.setField(acc, "id", 1L);

        assertNotEquals(null, acc);
        assertNotEquals(acc, new Object());
    }

    @Test
    @DisplayName("Should be equal to itself")
    void shouldBeEqualToItself() {
        Account acc = new Account(user, BigDecimal.TEN);
        assertEquals(acc, acc);
    }

    @Test
    @DisplayName("Should get version")
    void shouldGetVersion() {
        ReflectionTestUtils.setField(account, "version", 1L);
        assertEquals(1L, account.getVersion());
    }
}
