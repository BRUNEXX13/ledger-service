package com.astropay.domain.model.account;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, Long> {

    @Override
    Optional<Account> findById(Long id);

    Optional<Account> findByUser_Id(Long userId);

    @Query("SELECT a FROM Account a WHERE a.id IN :ids")
    List<Account> findByIds(@Param("ids") List<Long> ids);

    // --- NOVO MÉTODO PARA BATCH SEGURO ---
    // 1. PESSIMISTIC_WRITE: Gera um SELECT ... FOR UPDATE. Bloqueia leitura/escrita de outros.
    // 2. ORDER BY id: Garante a ordem de bloqueio para evitar Deadlocks.
    // 3. QueryHints: Define timeout para não travar a thread infinitamente se o banco estiver lento.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints({@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000")})
    @Query("SELECT a FROM Account a WHERE a.id IN :ids ORDER BY a.id")
    List<Account> findByIdsAndLock(@Param("ids") List<Long> ids);

    @Override
    <S extends Account> S save(S entity);

    @Override
    void delete(Account entity);

    @Override
    void deleteById(Long id);
}
