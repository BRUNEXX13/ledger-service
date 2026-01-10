package com.bss.domain.transaction;

import com.bss.domain.account.Account;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TransactionTest {

    private Account sender;
    private Account receiver;
    private UUID idempotencyKey;

    @BeforeEach
    void setUp() {
        sender = Mockito.mock(Account.class);
        receiver = Mockito.mock(Account.class);
        idempotencyKey = UUID.randomUUID();
    }

    @Test
    @DisplayName("Should create transaction with PENDING status")
    void shouldCreateTransactionWithPendingStatus() {
        Transaction transaction = new Transaction(sender, receiver, new BigDecimal("100.00"), idempotencyKey);

        assertEquals(TransactionStatus.PENDING, transaction.getStatus());
        assertEquals(sender, transaction.getSender());
        assertEquals(receiver, transaction.getReceiver());
        assertEquals(new BigDecimal("100.00"), transaction.getAmount());
        assertEquals(idempotencyKey, transaction.getIdempotencyKey());
        assertNull(transaction.getFailureReason());
    }

    @Test
    @DisplayName("Should complete transaction successfully")
    void shouldCompleteTransactionSuccessfully() {
        Transaction transaction = new Transaction(sender, receiver, new BigDecimal("100.00"), idempotencyKey);
        
        transaction.complete();

        assertEquals(TransactionStatus.SUCCESS, transaction.getStatus());
        assertNull(transaction.getFailureReason());
    }

    @Test
    @DisplayName("Should fail transaction with reason")
    void shouldFailTransactionWithReason() {
        Transaction transaction = new Transaction(sender, receiver, new BigDecimal("100.00"), idempotencyKey);
        String reason = "Insufficient funds";

        transaction.fail(reason);

        assertEquals(TransactionStatus.FAILED, transaction.getStatus());
        assertEquals(reason, transaction.getFailureReason());
    }

    @Test
    @DisplayName("Should verify equality based on ID")
    void shouldVerifyEqualityBasedOnId() {
        Transaction tx1 = new Transaction(sender, receiver, BigDecimal.TEN, UUID.randomUUID());
        Transaction tx2 = new Transaction(sender, receiver, BigDecimal.TEN, UUID.randomUUID());
        Transaction tx3 = new Transaction(sender, receiver, BigDecimal.TEN, UUID.randomUUID());

        // Set IDs using Reflection since setId is not public/available
        ReflectionTestUtils.setField(tx1, "id", 1L);
        ReflectionTestUtils.setField(tx2, "id", 1L);
        ReflectionTestUtils.setField(tx3, "id", 2L);

        // Same ID -> Equal
        assertEquals(tx1, tx2);
        assertEquals(tx1.hashCode(), tx2.hashCode());

        // Different ID -> Not Equal
        assertNotEquals(tx1, tx3);
        assertNotEquals(tx1.hashCode(), tx3.hashCode());
    }

    @Test
    @DisplayName("Should not be equal to null or different class")
    void shouldNotBeEqualToNullOrDifferentClass() {
        Transaction tx = new Transaction(sender, receiver, BigDecimal.TEN, UUID.randomUUID());
        ReflectionTestUtils.setField(tx, "id", 1L);

        assertNotEquals(null, tx);
        assertNotEquals(tx, new Object());
    }

    @Test
    @DisplayName("Should be equal to itself")
    void shouldBeEqualToItself() {
        Transaction tx = new Transaction(sender, receiver, BigDecimal.TEN, UUID.randomUUID());
        assertEquals(tx, tx);
    }
}
