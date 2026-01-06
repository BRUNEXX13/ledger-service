package com.bss.domain.account;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, Long> {

    @Override
    Optional<Account> findById(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Account a WHERE a.id = :id")
    Optional<Account> findByIdForUpdate(@Param("id") Long id);

    Optional<Account> findByUser_Id(Long userId);

    @Query("SELECT a FROM Account a WHERE a.id IN :ids")
    List<Account> findByIds(@Param("ids") List<Long> ids);

    @Override
    <S extends Account> S save(S entity);

    @Override
    void delete(Account entity);
}
