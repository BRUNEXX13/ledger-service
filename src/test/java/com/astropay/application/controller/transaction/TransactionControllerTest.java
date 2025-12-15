package com.astropay.application.controller.transaction;

import com.astropay.application.dto.response.transaction.TransactionResponse;
import com.astropay.application.dto.response.transaction.TransactionUserResponse;
import com.astropay.application.exception.handler.RestExceptionHandler;
import com.astropay.application.service.transaction.TransactionAuditService;
import com.astropay.application.service.transaction.port.in.TransactionService;
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
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TransactionController.class)
@ContextConfiguration(classes = TransactionControllerTest.TestConfig.class)
class TransactionControllerTest {

    @Configuration
    @EnableAutoConfiguration
    @Import({TransactionController.class, RestExceptionHandler.class})
    static class TestConfig {
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TransactionService transactionService;

    @MockitoBean
    private TransactionAuditService transactionAuditService;
    
    @MockitoBean
    private PagedResourcesAssembler<TransactionResponse> pagedResourcesAssembler;

    @Test
    @DisplayName("GET /transactions/{id} - Should return 200 OK for existing transaction")
    void shouldGetTransactionById() throws Exception {
        Long transactionId = 1L;
        TransactionResponse response = new TransactionResponse(transactionId, 1L, 2L, BigDecimal.TEN, "SUCCESS", null, UUID.randomUUID());
        response.setCreatedAt(LocalDateTime.now());
        when(transactionService.findTransactionById(transactionId)).thenReturn(response);

        mockMvc.perform(get("/transactions/{id}", transactionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(transactionId));
    }

    @Test
    @DisplayName("GET /transactions - Should return 200 OK with a page of transactions")
    void shouldGetAllTransactions() throws Exception {
        TransactionResponse response = new TransactionResponse(1L, 1L, 2L, BigDecimal.TEN, "SUCCESS", null, UUID.randomUUID());
        response.setCreatedAt(LocalDateTime.now());
        Page<TransactionResponse> page = new PageImpl<>(Collections.singletonList(response));
        when(transactionService.findAllTransactions(any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/transactions"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /transactions/{id}/sender - Should return 200 OK with sender info")
    void shouldGetTransactionSender() throws Exception {
        Long transactionId = 1L;
        TransactionUserResponse response = new TransactionUserResponse(UUID.randomUUID(), transactionId, "John Doe", "john.doe@example.com", "12345678900", LocalDateTime.now());
        when(transactionAuditService.findUserByTransactionId(transactionId)).thenReturn(response);

        mockMvc.perform(get("/transactions/{id}/sender", transactionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.senderName").value("John Doe"));
    }
}
