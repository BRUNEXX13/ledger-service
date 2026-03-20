-- =================================================================
-- V2: Add Unique Constraint to Outbox Table for Idempotency
-- =================================================================

-- Cria um índice único combinando aggregate_id (Idempotency Key) e event_type.
-- Isso impede que a aplicação insira eventos duplicados para a mesma operação.
CREATE UNIQUE INDEX uk_outbox_aggregate_event ON tb_outbox_event(aggregate_id, event_type);
