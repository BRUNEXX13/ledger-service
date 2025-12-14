package com.astropay.domain.model.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    /**
     * Busca os eventos mais antigos que ainda não foram processados.
     * A ordenação por 'createdAt' garante o processamento na ordem de criação.
     */
    List<OutboxEvent> findTop100ByOrderByCreatedAtAsc();
}
