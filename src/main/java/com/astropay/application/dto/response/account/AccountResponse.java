package com.astropay.application.dto.response.account;

import com.astropay.application.util.AppConstants;
import com.astropay.domain.model.account.AccountStatus;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonRootName;
import org.springframework.hateoas.RepresentationModel;
import org.springframework.hateoas.server.core.Relation;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

@JsonRootName(value = "account")
@Relation(collectionRelation = "accounts")
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AccountResponse extends RepresentationModel<AccountResponse> {

    private Long id;
    private Long userId;
    private BigDecimal balance;
    private AccountStatus status;

    @JsonFormat(pattern = AppConstants.DATE_TIME_FORMAT, timezone = "UTC")
    private Instant createdAt;

    @JsonFormat(pattern = AppConstants.DATE_TIME_FORMAT, timezone = "UTC")
    private Instant updatedAt;

    /**
     * Construtor padrão exigido por frameworks como Jackson para deserialização.
     * Suprimindo o aviso "unused" pois é invocado via reflexão.
     */
    @SuppressWarnings("unused")
    public AccountResponse() {
    }

    public AccountResponse(Long id, Long userId, BigDecimal balance, AccountStatus status, Instant createdAt, Instant updatedAt) {
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
    public BigDecimal getBalance() { return balance; }
    public void setBalance(BigDecimal balance) { this.balance = balance; }
    public AccountStatus getStatus() { return status; }
    public void setStatus(AccountStatus status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        AccountResponse that = (AccountResponse) o;
        return Objects.equals(id, that.id) &&
                Objects.equals(userId, that.userId) &&
                Objects.equals(balance, that.balance) &&
                status == that.status &&
                Objects.equals(createdAt, that.createdAt) &&
                Objects.equals(updatedAt, that.updatedAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), id, userId, balance, status, createdAt, updatedAt);
    }
}
