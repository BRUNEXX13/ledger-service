package com.astropay.application.controller.transfer;

import com.astropay.application.controller.transfer.mapper.TransferMapper;
import com.astropay.application.dto.request.transfer.TransferRequest;
import com.astropay.application.exception.handler.RestExceptionHandler;
import com.astropay.application.service.transfer.TransferService;
import com.astropay.domain.model.account.InsufficientBalanceException;
import com.astropay.domain.model.transfer.Transfer;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TransferController.class)
@ContextConfiguration(classes = TransferControllerTest.TestConfig.class)
class TransferControllerTest {

    @Configuration
    @EnableAutoConfiguration
    @Import({TransferController.class, RestExceptionHandler.class})
    static class TestConfig {
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private TransferService transferService;

    @MockitoBean
    private TransferMapper transferMapper;

    private TransferRequest transferRequest;
    private Transfer transfer;

    @BeforeEach
    void setUp() {
        transferRequest = createTransferRequest(1L, 2L, BigDecimal.TEN);
        transfer = createTransfer(transferRequest);
    }

    private TransferRequest createTransferRequest(Long senderId, Long receiverId, BigDecimal amount) {
        TransferRequest request = new TransferRequest();
        request.setIdempotencyKey(UUID.randomUUID());
        request.setSenderAccountId(senderId);
        request.setReceiverAccountId(receiverId);
        request.setAmount(amount);
        return request;
    }

    private Transfer createTransfer(TransferRequest request) {
        return new Transfer(
                request.getSenderAccountId(),
                request.getReceiverAccountId(),
                request.getAmount(),
                request.getIdempotencyKey()
        );
    }

    @Test
    @DisplayName("POST /transfers - Should return 202 Accepted for valid transfer request")
    void transfer_whenSuccessful_shouldReturnAccepted() throws Exception {
        when(transferMapper.toDomain(any(TransferRequest.class))).thenReturn(transfer);
        // Corrected: Use when/thenReturn for non-void methods.
        // Returning null is sufficient as the controller ignores the return value.
        when(transferService.transfer(any(Transfer.class))).thenReturn(null);

        mockMvc.perform(post("/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(transferRequest)))
                .andExpect(status().isAccepted());
    }

    @Test
    @DisplayName("POST /transfers - Should return 422 Unprocessable Entity for insufficient balance")
    void transfer_whenInsufficientBalance_shouldReturnUnprocessableEntity() throws Exception {
        when(transferMapper.toDomain(any(TransferRequest.class))).thenReturn(transfer);
        doThrow(new InsufficientBalanceException("Insufficient balance"))
                .when(transferService).transfer(any(Transfer.class));

        mockMvc.perform(post("/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(transferRequest)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("Insufficient balance"));
    }

    @Test
    @DisplayName("POST /transfers - Should return 400 Bad Request for illegal argument")
    void transfer_whenIllegalArgument_shouldReturnBadRequest() throws Exception {
        when(transferMapper.toDomain(any(TransferRequest.class))).thenReturn(transfer);
        doThrow(new IllegalArgumentException("Invalid argument"))
                .when(transferService).transfer(any(Transfer.class));

        mockMvc.perform(post("/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(transferRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid argument"));
    }

    @Test
    @DisplayName("POST /transfers - Should return 500 Internal Server Error for unexpected error")
    void transfer_whenUnexpectedError_shouldReturnInternalServerError() throws Exception {
        when(transferMapper.toDomain(any(TransferRequest.class))).thenReturn(transfer);
        doThrow(new RuntimeException("Unexpected error"))
                .when(transferService).transfer(any(Transfer.class));

        mockMvc.perform(post("/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(transferRequest)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("An unexpected internal server error has occurred."));
    }

    @Test
    @DisplayName("POST /transfers - Should return 400 Bad Request for invalid request body")
    void transfer_whenInvalidRequest_shouldReturnBadRequest() throws Exception {
        TransferRequest invalidRequest = new TransferRequest();
        // Missing required fields

        mockMvc.perform(post("/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());
    }
}
