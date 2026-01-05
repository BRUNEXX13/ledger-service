package com.bss.application.controller.account;

import com.bss.application.dto.request.account.UpdateAccountRequest;
import com.bss.application.dto.response.account.AccountResponse;
import com.bss.application.exception.handler.RestExceptionHandler;
import com.bss.application.service.account.port.in.AccountService;
import com.bss.domain.account.AccountStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AccountController.class)
@ContextConfiguration(classes = AccountControllerTest.TestConfig.class)
class AccountControllerTest {

    @Configuration
    @EnableAutoConfiguration
    @Import({AccountController.class, RestExceptionHandler.class})
    static class TestConfig {
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AccountService accountService;
    
    @MockitoBean
    private PagedResourcesAssembler<AccountResponse> pagedResourcesAssembler;

    @Test
    @DisplayName("GET /accounts/{id} - Should return 200 OK for existing account")
    void shouldGetAccountById() throws Exception {
        Long accountId = 1L;
        AccountResponse response = new AccountResponse(accountId, 1L, new BigDecimal("100.00"), AccountStatus.ACTIVE, LocalDateTime.now(), LocalDateTime.now());
        when(accountService.findAccountById(accountId)).thenReturn(response);

        mockMvc.perform(get("/accounts/{id}", accountId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(accountId));
    }

    @Test
    @DisplayName("GET /accounts - Should return 200 OK with a page of accounts")
    void shouldGetAllAccounts() throws Exception {
        Page<AccountResponse> page = new PageImpl<>(Collections.emptyList());
        when(accountService.findAllAccounts(any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/accounts"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("PUT /accounts/{id} - Should return 200 OK for successful update")
    void shouldUpdateAccount() throws Exception {
        Long accountId = 1L;
        UpdateAccountRequest request = new UpdateAccountRequest(new BigDecimal("200.00"));
        AccountResponse response = new AccountResponse(accountId, 1L, new BigDecimal("200.00"), AccountStatus.ACTIVE, LocalDateTime.now(), LocalDateTime.now());
        when(accountService.updateAccount(any(Long.class), any(UpdateAccountRequest.class))).thenReturn(response);

        mockMvc.perform(put("/accounts/{id}", accountId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(200.00));
    }

    @Test
    @DisplayName("DELETE /accounts/{id} - Should return 204 No Content for successful inactivation")
    void shouldInactivateAccount() throws Exception {
        Long accountId = 1L;
        doNothing().when(accountService).inactivateAccount(accountId);

        mockMvc.perform(delete("/accounts/{id}", accountId))
                .andExpect(status().isNoContent());
    }
}
