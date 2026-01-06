package com.bss.domain.account;

import com.bss.domain.user.User;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.*;
import org.hibernate.annotations.Check;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "tb_account")
@EntityListeners(AuditingEntityListener.class)
@Check(constraints = "balance >= 0")
public class Account implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Long version; // Field for optimistic locking

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(nullable = false)
    private BigDecimal balance;

    @Enumerated(EnumType.ORDINAL)
    @Column(nullable = false)
    private AccountStatus status;

    @CreatedDate
    @JsonFormat(pattern = "MM/dd/yyyy HH:mm:ss.SSS")
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @JsonFormat(pattern = "MM/dd/yyyy HH:mm:ss.SSS")
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Deprecated
    protected Account() {}

    public Account(User user, BigDecimal balance) {
        this.user = user;
        this.balance = balance;
        this.status = AccountStatus.ACTIVE; // New account always starts as active
    }

    // Getters...
    public Long getId() { return id; }
    public Long getVersion() { return version; }
    public User getUser() { return user; }
    public BigDecimal getBalance() { return balance; }
    public AccountStatus getStatus() { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    // Business methods
    public void deposit(BigDecimal amount) {
        if (this.status != AccountStatus.ACTIVE) {
            throw new IllegalStateException("Account is not active. Cannot deposit.");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Deposit amount must be positive.");
        }
        this.balance = this.balance.add(amount);
    }

    public void withdraw(BigDecimal amount) {
        if (this.status != AccountStatus.ACTIVE) {
            throw new IllegalStateException("Account is not active. Cannot withdraw.");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Withdrawal amount must be positive.");
        }
        if (this.balance.compareTo(amount) < 0) {
            throw new InsufficientBalanceException("Insufficient balance for withdrawal. Current: " + this.balance + ", Required: " + amount);
        }
        this.balance = this.balance.subtract(amount);
    }

    public void adjustBalance(BigDecimal newBalance) {
        if (newBalance == null || newBalance.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("New balance cannot be null or negative.");
        }
        this.balance = newBalance;
    }

    public void block() {
        this.status = AccountStatus.BLOCKED;
    }

    public void inactivate() {
        if (this.balance.compareTo(BigDecimal.ZERO) != 0) {
            throw new IllegalStateException("Cannot inactivate account with a non-zero balance.");
        }
        this.status = AccountStatus.INACTIVE;
    }

    public void activate() {
        this.status = AccountStatus.ACTIVE;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Account account = (Account) o;
        return id != null && Objects.equals(id, account.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
