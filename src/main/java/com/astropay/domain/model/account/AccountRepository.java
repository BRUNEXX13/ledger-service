package com.astropay.domain.model.account;

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

    @Cacheable(value = "accounts", key = "'user:' + #userId")
    Optional<Account> findByUser_Id(Long userId);

    @Cacheable(value = "accounts", key = "#ids")
    @Query("SELECT a FROM Account a WHERE a.id IN :ids")
    List<Account> findByIds(@Param("ids") List<Long> ids);

    @Override
    @Caching(
        put = { @CachePut(value = "accounts", key = "#entity.id") },
        evict = { @CacheEvict(value = "accounts", key = "'user:' + #entity.user.id") }
    )
    <S extends Account> S save(S entity);

    @Override
    @Caching(
        evict = {
            @CacheEvict(value = "accounts", key = "#id"),
            // We need to fetch the entity to know the userId and invalidate the other cache.
            // This can be complex. An alternative is to invalidate the entire cache.
            // For simplicity, we will focus on invalidating by ID.
            // The search by UserID will be outdated until the TTL expires.
            // For a critical system, an extra step would be needed here.
            @CacheEvict(value = "accounts", allEntries = true) // Safer, but more aggressive alternative
        }
    )
    void deleteById(Long id);
}
