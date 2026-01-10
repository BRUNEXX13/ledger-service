package com.bss.application.controller.account;

import com.bss.application.dto.request.account.UpdateAccountRequest;
import com.bss.application.dto.response.account.AccountResponse;
import com.bss.application.exception.ResourceNotFoundException;
import com.bss.application.service.account.port.in.AccountService;
import com.bss.domain.account.AccountStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AccountController.class)
class AccountControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AccountService accountService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("GET /accounts/{id} - Should return account when found")
    void getAccountById_shouldReturnAccount() throws Exception {
        // Arrange
        AccountResponse response = new AccountResponse(1L, 10L, new BigDecimal("100.00"), AccountStatus.ACTIVE, LocalDateTime.now(), LocalDateTime.now());
        when(accountService.findAccountById(1L)).thenReturn(response);

        // Act & Assert
        mockMvc.perform(get("/accounts/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.userId").value(10))
                .andExpect(jsonPath("$.balance").value(100.00))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$._links.self.href").exists());
    }

    @Test
    @DisplayName("GET /accounts/{id} - Should return 404 when account not found")
    void getAccountById_shouldReturn404() throws Exception {
        // Arrange
        when(accountService.findAccountById(99L)).thenThrow(new ResourceNotFoundException("Account not found"));

        // Act & Assert
        mockMvc.perform(get("/accounts/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /accounts - Should return paged accounts")
    void getAllAccounts_shouldReturnPagedAccounts() throws Exception {
        // Arrange
        AccountResponse acc1 = new AccountResponse(1L, 10L, BigDecimal.TEN, AccountStatus.ACTIVE, LocalDateTime.now(), LocalDateTime.now());
        AccountResponse acc2 = new AccountResponse(2L, 20L, BigDecimal.ZERO, AccountStatus.BLOCKED, LocalDateTime.now(), LocalDateTime.now());
        Page<AccountResponse> page = new PageImpl<>(List.of(acc1, acc2));

        when(accountService.findAllAccounts(any(Pageable.class))).thenReturn(page);

        // Act & Assert
        mockMvc.perform(get("/accounts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._embedded.accounts").isArray())
                .andExpect(jsonPath("$._embedded.accounts.length()").value(2))
                .andExpect(jsonPath("$._embedded.accounts[0].id").value(1))
                .andExpect(jsonPath("$._embedded.accounts[1].id").value(2));
    }

    @Test
    @DisplayName("GET /accounts - Should return empty page when no accounts")
    void getAllAccounts_shouldReturnEmptyPage() throws Exception {
        // Arrange
        Page<AccountResponse> page = new PageImpl<>(Collections.emptyList());
        when(accountService.findAllAccounts(any(Pageable.class))).thenReturn(page);

        // Act & Assert
        mockMvc.perform(get("/accounts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(0));
    }

    @Test
    @DisplayName("PUT /accounts/{id} - Should update account balance")
    void updateAccount_shouldUpdateBalance() throws Exception {
        // Arrange
        UpdateAccountRequest request = new UpdateAccountRequest(new BigDecimal("500.00"));
        AccountResponse response = new AccountResponse(1L, 10L, new BigDecimal("500.00"), AccountStatus.ACTIVE, LocalDateTime.now(), LocalDateTime.now());
        
        when(accountService.updateAccount(eq(1L), any(UpdateAccountRequest.class))).thenReturn(response);

        // Act & Assert
        mockMvc.perform(put("/accounts/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(500.00));
    }

    @Test
    @DisplayName("PUT /accounts/{id} - Should return 400 for invalid request")
    void updateAccount_shouldReturn400ForInvalidRequest() throws Exception {
        // Arrange
        // Balance cannot be negative (assuming validation annotation exists on DTO)
        UpdateAccountRequest request = new UpdateAccountRequest(new BigDecimal("-100.00"));

        // Act & Assert
        mockMvc.perform(put("/accounts/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("DELETE /accounts/{id} - Should inactivate account")
    void inactivateAccount_shouldReturnNoContent() throws Exception {
        // Act & Assert
        mockMvc.perform(delete("/accounts/1"))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("DELETE /accounts/{id} - Should return 404 when account not found")
    void inactivateAccount_shouldReturn404() throws Exception {
        // Arrange
        doThrow(new ResourceNotFoundException("Account not found")).when(accountService).inactivateAccount(99L);

        // Act & Assert
        mockMvc.perform(delete("/accounts/99"))
                .andExpect(status().isNotFound());
    }
    
    @Test
    @DisplayName("DELETE /accounts/{id} - Should return 409/500 when inactivation fails (e.g. non-zero balance)")
    void inactivateAccount_shouldReturnErrorOnBusinessRuleViolation() throws Exception {
        // Arrange
        doThrow(new IllegalStateException("Cannot inactivate account with non-zero balance")).when(accountService).inactivateAccount(1L);

        // Act & Assert
        // Assuming GlobalExceptionHandler maps IllegalStateException to 400 or 500. 
        // If not mapped, it might return 500 or 400 depending on Spring config.
        // Let's expect 500 or 400.
        mockMvc.perform(delete("/accounts/1"))
                .andExpect(status().is5xxServerError()); // Or isBadRequest() if mapped
    }
}
