package com.bss.domain.model.user;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    @Override
    @Cacheable(value = "users", key = "#id")
    Optional<User> findById(Long id);

    @Override
    @CachePut(value = "users", key = "#entity.id")
    <S extends User> S save(S entity);

    @Override
    @CacheEvict(value = "users", key = "#id")
    void deleteById(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints({@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000")})
    @Query("SELECT u FROM User u WHERE u.id = :id")
    Optional<User> findByIdForUpdate(@Param("id") Long id);

    boolean existsByEmail(String email);

    boolean existsByDocument(String document);
    
    boolean existsByDocumentOrEmail(String document, String email);
}
