package com.bss.application.dto.response.account;

import com.bss.domain.account.AccountStatus;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonRootName;
import org.springframework.hateoas.RepresentationModel;
import org.springframework.hateoas.server.core.Relation;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@JsonRootName(value = "account")
@Relation(collectionRelation = "accounts")
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AccountResponse extends RepresentationModel<AccountResponse> {

    private Long id;
    private Long userId;
    private BigDecimal balance;
    private AccountStatus status;

    @JsonFormat(pattern = "MM/dd/yyyy HH:mm:ss.SSS")
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "MM/dd/yyyy HH:mm:ss.SSS")
    private LocalDateTime updatedAt;

    /**
     * Construtor padrão exigido pelo Jackson para deserialização.
     */
    public AccountResponse() {
    }

    public AccountResponse(Long id, Long userId, BigDecimal balance, AccountStatus status, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.userId = userId;
        this.balance = balance;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    // Getters e Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public BigDecimal getBalance() { return balance; }
    public void setBalance(BigDecimal balance) { this.balance = balance; }
    public AccountStatus getStatus() { return status; }
    public void setStatus(AccountStatus status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
