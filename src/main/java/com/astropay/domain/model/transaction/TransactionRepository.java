package com.astropay.domain.model.transaction;

import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.jpa.repository.JpaRepository;

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
