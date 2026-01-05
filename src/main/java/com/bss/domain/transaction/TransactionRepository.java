package com.bss.domain.transaction;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    @Override
    Optional<Transaction> findById(Long id);

    Optional<Transaction> findByIdempotencyKey(UUID idempotencyKey);

    @Override
    <S extends Transaction> S save(S entity);
}
