package com.bss.domain.account;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, Long> {

    @Override
    @Cacheable(value = "accounts", key = "#id")
    Optional<Account> findById(Long id);

    // Cache removido temporariamente para depuração
    Optional<Account> findByUser_Id(Long userId);

    @Cacheable(value = "accounts", key = "#ids")
    @Query("SELECT a FROM Account a WHERE a.id IN :ids")
    List<Account> findByIds(@Param("ids") List<Long> ids);

    @Override
    @Caching(
        put = {
            @CachePut(value = "accounts", key = "#entity.id")
            // A anotação para 'user:' foi removida pois o cache principal não existe mais
        }
    )
    <S extends Account> S save(S entity);

    @Override
    @Caching(
        evict = {
            @CacheEvict(value = "accounts", key = "#entity.id")
            // A anotação para 'user:' foi removida pois o cache principal não existe mais
        }
    )
    void delete(Account entity);

    @Override
    void deleteById(Long id); // This will no longer have caching annotations
}
