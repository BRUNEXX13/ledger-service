package com.astropay.domain.model.account;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, Long> {

    Optional<Account> findByUser_Id(Long userId);

    @Cacheable(value = "accounts", key = "#ids")
    @Query("SELECT a FROM Account a WHERE a.id IN :ids")
    List<Account> findByIds(@Param("ids") List<Long> ids);
}
