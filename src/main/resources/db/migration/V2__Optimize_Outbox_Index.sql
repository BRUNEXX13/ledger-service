-- Otimização de índice para a query de polling do Outbox Pattern
-- A query faz filtro por status e event_type, e ordena por created_at.
-- O índice anterior (status, event_type) forçava um Sort em memória/disco custoso com milhões de registros.

DROP INDEX IF EXISTS idx_outbox_status_type;

CREATE INDEX idx_outbox_status_type_created
ON tb_outbox_event (status, event_type, created_at);
