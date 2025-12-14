package com.astropay.domain.model.transaction;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    /**
     * Busca uma transação pelo seu ID, já trazendo (FETCH) a conta remetente
     * e o usuário remetente em uma única consulta para otimizar a performance.
     *
     * @param id O ID da transação.
     * @return Um Optional contendo a transação com os dados carregados.
     */
    @Query("SELECT t FROM Transaction t JOIN FETCH t.sender s JOIN FETCH s.user WHERE t.id = :id")
    Optional<Transaction> findByIdWithSenderAndUser(@Param("id") Long id);
}
