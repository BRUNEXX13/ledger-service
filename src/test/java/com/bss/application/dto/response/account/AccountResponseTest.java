package com.bss.application.dto.response.account;

import com.bss.domain.account.AccountStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.hateoas.Link;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccountResponseTest {

    @Test
    @DisplayName("Should verify equals contract")
    void testEqualsContract() {
        LocalDateTime now = LocalDateTime.now();
        AccountResponse response1 = new AccountResponse(1L, 100L, BigDecimal.TEN, AccountStatus.ACTIVE, now, now);
        AccountResponse response2 = new AccountResponse(1L, 100L, BigDecimal.TEN, AccountStatus.ACTIVE, now, now);
        
        // 1. Reflexive (this == o)
        assertTrue(response1.equals(response1));
        
        // 2. Symmetric
        assertTrue(response1.equals(response2));
        assertTrue(response2.equals(response1));
        
        // 3. Null check
        assertFalse(response1.equals(null));
        
        // 4. Different class check
        assertFalse(response1.equals(new Object()));
        
        // 5. Consistent
        assertTrue(response1.equals(response2));
    }

    @Test
    @DisplayName("Should verify equals with different fields")
    void testNotEqualsWithDifferentFields() {
        LocalDateTime now = LocalDateTime.now();
        AccountResponse base = new AccountResponse(1L, 100L, BigDecimal.TEN, AccountStatus.ACTIVE, now, now);
        
        assertNotEquals(base, new AccountResponse(2L, 100L, BigDecimal.TEN, AccountStatus.ACTIVE, now, now));
        assertNotEquals(base, new AccountResponse(1L, 200L, BigDecimal.TEN, AccountStatus.ACTIVE, now, now));
        assertNotEquals(base, new AccountResponse(1L, 100L, BigDecimal.ONE, AccountStatus.ACTIVE, now, now));
        assertNotEquals(base, new AccountResponse(1L, 100L, BigDecimal.TEN, AccountStatus.BLOCKED, now, now));
        assertNotEquals(base, new AccountResponse(1L, 100L, BigDecimal.TEN, AccountStatus.ACTIVE, now.plusDays(1), now));
        assertNotEquals(base, new AccountResponse(1L, 100L, BigDecimal.TEN, AccountStatus.ACTIVE, now, now.plusDays(1)));
    }
    
    @Test
    @DisplayName("Should verify equals with super class (RepresentationModel)")
    void testEqualsWithSuperClass() {
        LocalDateTime now = LocalDateTime.now();
        AccountResponse response1 = new AccountResponse(1L, 100L, BigDecimal.TEN, AccountStatus.ACTIVE, now, now);
        AccountResponse response2 = new AccountResponse(1L, 100L, BigDecimal.TEN, AccountStatus.ACTIVE, now, now);
        
        // Add links to response1 but not response2
        response1.add(Link.of("/self"));
        
        // Should be not equal because super.equals checks links
        assertNotEquals(response1, response2);
        
        // Add same link to response2
        response2.add(Link.of("/self"));
        
        // Should be equal now
        assertEquals(response1, response2);
    }

    @Test
    @DisplayName("Should verify hashCode contract")
    void testHashCode() {
        LocalDateTime now = LocalDateTime.now();
        AccountResponse response1 = new AccountResponse(1L, 100L, BigDecimal.TEN, AccountStatus.ACTIVE, now, now);
        AccountResponse response2 = new AccountResponse(1L, 100L, BigDecimal.TEN, AccountStatus.ACTIVE, now, now);

        assertEquals(response1.hashCode(), response2.hashCode());
        
        // Change a field
        response2.setId(2L);
        assertNotEquals(response1.hashCode(), response2.hashCode());
    }

    @Test
    @DisplayName("Should verify getters and setters")
    void testGettersAndSetters() {
        AccountResponse response = new AccountResponse();
        LocalDateTime now = LocalDateTime.now();

        response.setId(1L);
        response.setUserId(100L);
        response.setBalance(BigDecimal.TEN);
        response.setStatus(AccountStatus.ACTIVE);
        response.setCreatedAt(now);
        response.setUpdatedAt(now);

        assertEquals(1L, response.getId());
        assertEquals(100L, response.getUserId());
        assertEquals(BigDecimal.TEN, response.getBalance());
        assertEquals(AccountStatus.ACTIVE, response.getStatus());
        assertEquals(now, response.getCreatedAt());
        assertEquals(now, response.getUpdatedAt());
    }
}
