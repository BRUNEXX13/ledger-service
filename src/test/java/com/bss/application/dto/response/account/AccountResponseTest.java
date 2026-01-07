package com.bss.application.dto.response.account;

import com.bss.domain.account.AccountStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class AccountResponseTest {

    @Test
    void testEqualsAndHashCode() {
        LocalDateTime now = LocalDateTime.now();
        AccountResponse response1 = new AccountResponse(1L, 100L, BigDecimal.TEN, AccountStatus.ACTIVE, now, now);
        AccountResponse response2 = new AccountResponse(1L, 100L, BigDecimal.TEN, AccountStatus.ACTIVE, now, now);
        AccountResponse response3 = new AccountResponse(2L, 100L, BigDecimal.TEN, AccountStatus.ACTIVE, now, now);

        assertThat(response1).isEqualTo(response2);
        assertThat(response1).hasSameHashCodeAs(response2);
        assertThat(response1).isNotEqualTo(response3);
    }

    @Test
    void testGettersAndSetters() {
        AccountResponse response = new AccountResponse();
        LocalDateTime now = LocalDateTime.now();

        response.setId(1L);
        response.setUserId(100L);
        response.setBalance(BigDecimal.TEN);
        response.setStatus(AccountStatus.ACTIVE);
        response.setCreatedAt(now);
        response.setUpdatedAt(now);

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getUserId()).isEqualTo(100L);
        assertThat(response.getBalance()).isEqualTo(BigDecimal.TEN);
        assertThat(response.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(response.getCreatedAt()).isEqualTo(now);
        assertThat(response.getUpdatedAt()).isEqualTo(now);
    }
    
    @Test
    void testNotEqualsWithDifferentFields() {
        LocalDateTime now = LocalDateTime.now();
        AccountResponse base = new AccountResponse(1L, 100L, BigDecimal.TEN, AccountStatus.ACTIVE, now, now);
        
        assertThat(base).isNotEqualTo(new AccountResponse(2L, 100L, BigDecimal.TEN, AccountStatus.ACTIVE, now, now));
        assertThat(base).isNotEqualTo(new AccountResponse(1L, 200L, BigDecimal.TEN, AccountStatus.ACTIVE, now, now));
        assertThat(base).isNotEqualTo(new AccountResponse(1L, 100L, BigDecimal.ONE, AccountStatus.ACTIVE, now, now));
        assertThat(base).isNotEqualTo(new AccountResponse(1L, 100L, BigDecimal.TEN, AccountStatus.BLOCKED, now, now));
        assertThat(base).isNotEqualTo(new AccountResponse(1L, 100L, BigDecimal.TEN, AccountStatus.ACTIVE, now.plusDays(1), now));
        assertThat(base).isNotEqualTo(new AccountResponse(1L, 100L, BigDecimal.TEN, AccountStatus.ACTIVE, now, now.plusDays(1)));
        assertThat(base).isNotEqualTo(null);
        assertThat(base).isNotEqualTo(new Object());
    }
}
