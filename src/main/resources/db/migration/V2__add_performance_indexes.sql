-- Adiciona um índice na chave estrangeira user_id na tabela de contas
CREATE INDEX idx_account_user_id ON tb_account(user_id);

-- Adiciona índices nas chaves estrangeiras da tabela de transações
CREATE INDEX idx_transaction_sender_id ON tb_transaction(sender_account_id);
CREATE INDEX idx_transaction_receiver_id ON tb_transaction(receiver_account_id);
