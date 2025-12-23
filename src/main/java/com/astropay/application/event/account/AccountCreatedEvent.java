package com.astropay.application.event.account;

import java.time.Instant;

public class AccountCreatedEvent {

    private Long accountId;
    private Long userId;
    private String userName;
    private String userEmail;
    private Instant createdAt;

    public AccountCreatedEvent() {
    }

    public AccountCreatedEvent(Long accountId, Long userId, String userName, String userEmail, Instant createdAt) {
        this.accountId = accountId;
        this.userId = userId;
        this.userName = userName;
        this.userEmail = userEmail;
        this.createdAt = createdAt;
    }

    public Long getAccountId() {
        return accountId;
    }

    public void setAccountId(Long accountId) {
        this.accountId = accountId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getUserEmail() {
        return userEmail;
    }

    public void setUserEmail(String userEmail) {
        this.userEmail = userEmail;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
