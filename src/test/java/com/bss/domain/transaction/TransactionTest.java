package com.bss.domain.transaction;

import com.bss.domain.account.Account;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.Mockito.mock;

class TransactionTest {

    @Test
    @DisplayName("Equals should return true for same object")
    void equals_ShouldReturnTrueForSameObject() {
        Transaction transaction = new Transaction(mock(Account.class), mock(Account.class), BigDecimal.TEN, UUID.randomUUID());
        assertEquals(transaction, transaction);
    }

    @Test
    @DisplayName("Equals should return false for null")
    void equals_ShouldReturnFalseForNull() {
        Transaction transaction = new Transaction(mock(Account.class), mock(Account.class), BigDecimal.TEN, UUID.randomUUID());
        assertNotEquals(null, transaction);
    }

    @Test
    @DisplayName("Equals should return false for different class")
    void equals_ShouldReturnFalseForDifferentClass() {
        Transaction transaction = new Transaction(mock(Account.class), mock(Account.class), BigDecimal.TEN, UUID.randomUUID());
        assertNotEquals(new Object(), transaction);
    }

    @Test
    @DisplayName("Equals should return true for same ID")
    void equals_ShouldReturnTrueForSameId() {
        Transaction t1 = new Transaction(mock(Account.class), mock(Account.class), BigDecimal.TEN, UUID.randomUUID());
        ReflectionTestUtils.setField(t1, "id", 1L);

        Transaction t2 = new Transaction(mock(Account.class), mock(Account.class), BigDecimal.ONE, UUID.randomUUID());
        ReflectionTestUtils.setField(t2, "id", 1L);

        assertEquals(t1, t2);
    }

    @Test
    @DisplayName("Equals should return false for different ID")
    void equals_ShouldReturnFalseForDifferentId() {
        Transaction t1 = new Transaction(mock(Account.class), mock(Account.class), BigDecimal.TEN, UUID.randomUUID());
        ReflectionTestUtils.setField(t1, "id", 1L);

        Transaction t2 = new Transaction(mock(Account.class), mock(Account.class), BigDecimal.TEN, UUID.randomUUID());
        ReflectionTestUtils.setField(t2, "id", 2L);

        assertNotEquals(t1, t2);
    }

    @Test
    @DisplayName("Equals should return false when one ID is null")
    void equals_ShouldReturnFalseWhenOneIdIsNull() {
        Transaction t1 = new Transaction(mock(Account.class), mock(Account.class), BigDecimal.TEN, UUID.randomUUID());
        ReflectionTestUtils.setField(t1, "id", 1L);

        Transaction t2 = new Transaction(mock(Account.class), mock(Account.class), BigDecimal.TEN, UUID.randomUUID());
        // t2 id is null

        assertNotEquals(t1, t2);
        assertNotEquals(t2, t1);
    }

    @Test
    @DisplayName("Equals should return false when both IDs are null")
    void equals_ShouldReturnFalseWhenBothIdsAreNull() {
        Transaction t1 = new Transaction(mock(Account.class), mock(Account.class), BigDecimal.TEN, UUID.randomUUID());
        Transaction t2 = new Transaction(mock(Account.class), mock(Account.class), BigDecimal.TEN, UUID.randomUUID());

        assertNotEquals(t1, t2);
    }

    @Test
    @DisplayName("HashCode should be equal for same ID")
    void hashCode_ShouldBeEqualForSameId() {
        Transaction t1 = new Transaction(mock(Account.class), mock(Account.class), BigDecimal.TEN, UUID.randomUUID());
        ReflectionTestUtils.setField(t1, "id", 1L);

        Transaction t2 = new Transaction(mock(Account.class), mock(Account.class), BigDecimal.ONE, UUID.randomUUID());
        ReflectionTestUtils.setField(t2, "id", 1L);

        assertEquals(t1.hashCode(), t2.hashCode());
    }

    @Test
    @DisplayName("HashCode should be different for different ID")
    void hashCode_ShouldBeDifferentForDifferentId() {
        Transaction t1 = new Transaction(mock(Account.class), mock(Account.class), BigDecimal.TEN, UUID.randomUUID());
        ReflectionTestUtils.setField(t1, "id", 1L);

        Transaction t2 = new Transaction(mock(Account.class), mock(Account.class), BigDecimal.TEN, UUID.randomUUID());
        ReflectionTestUtils.setField(t2, "id", 2L);

        assertNotEquals(t1.hashCode(), t2.hashCode());
    }
}
