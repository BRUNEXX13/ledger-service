package com.astropay.domain.model.transaction;

import com.astropay.domain.model.user.User;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    @Override
    @Cacheable(value = "transactions", key = "#id")
    Optional<Transaction> findById(Long id);

    @Cacheable(value = "transactions", key = "#idempotencyKey")
    Optional<Transaction> findByIdempotencyKey(UUID idempotencyKey);

    @Override
    @Caching(
        put = {
            @CachePut(value = "transactions", key = "#entity.id"),
            @CachePut(value = "transactions", key = "#entity.idempotencyKey")
        }
    )
    <S extends Transaction> S save(S entity);
}
