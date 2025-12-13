package com.astropay.domain.model.user;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Busca um usuário aplicando bloqueio pessimista.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints({@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000")})
    @Query("SELECT u FROM User u WHERE u.id = :id")
    Optional<User> findByIdForUpdate(@Param("id") Long id);

    /**
     * Verifica se existe algum usuário com este e-mail.
     */
    boolean existsByEmail(String email);

    /**
     * Verifica se existe algum usuário com este documento.
     */
    boolean existsByDocument(String document);
    
    // Mantemos este para compatibilidade se necessário, mas preferiremos os específicos acima
    boolean existsByDocumentOrEmail(String document, String email);
}
