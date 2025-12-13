package com.astropay.domain.model.transaction;

import com.astropay.domain.model.account.Account;
import jakarta.persistence.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "tb_transaction",
       uniqueConstraints = @UniqueConstraint(columnNames = "idempotencyKey", name = "uk_transaction_idempotency"))
@EntityListeners(AuditingEntityListener.class)
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_account_id", nullable = false)
    private Account sender;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receiver_account_id", nullable = false)
    private Account receiver;

    @Column(nullable = false)
    private BigDecimal amount;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false, unique = true)
    private UUID idempotencyKey;

    @Deprecated
    protected Transaction() {}

    public Transaction(Account sender, Account receiver, BigDecimal amount, UUID idempotencyKey) {
        this.sender = sender;
        this.receiver = receiver;
        this.amount = amount;
        this.idempotencyKey = idempotencyKey;
    }

    // Getters...
    public Long getId() { return id; }
    public Account getSender() { return sender; }
    public Account getReceiver() { return receiver; }
    public BigDecimal getAmount() { return amount; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public UUID getIdempotencyKey() { return idempotencyKey; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Transaction that = (Transaction) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
