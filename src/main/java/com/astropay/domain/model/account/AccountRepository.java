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
            // Precisamos buscar a entidade para saber o userId e invalidar o outro cache
            // Isso pode ser complexo. Uma alternativa é invalidar o cache inteiro.
            // Por simplicidade, vamos focar em invalidar pelo ID.
            // A busca por UserID ficará obsoleta até o TTL expirar.
            // Para um sistema crítico, seria necessário um passo a mais aqui.
            @CacheEvict(value = "accounts", allEntries = true) // Alternativa mais segura, porém mais agressiva
        }
    )
    void deleteById(Long id);
}
