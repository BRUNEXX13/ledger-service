-- =================================================================
-- CRIAÇÃO DAS TABELAS
-- =================================================================

-- Tabela de Usuários
CREATE TABLE tb_user (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    document VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL,
    status VARCHAR(50) NOT NULL,
    role VARCHAR(50) NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL
);
CREATE UNIQUE INDEX idx_user_document ON tb_user(document);
CREATE UNIQUE INDEX idx_user_email ON tb_user(email);

-- Tabela de Contas
CREATE TABLE tb_account (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE,
    balance NUMERIC(19, 2) NOT NULL,
    status VARCHAR(50) NOT NULL,
    version BIGINT,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    CONSTRAINT fk_account_user FOREIGN KEY (user_id) REFERENCES tb_user(id)
);
ALTER TABLE tb_account ADD CONSTRAINT balance_check CHECK (balance >= 0);

-- Tabela de Transações
CREATE TABLE tb_transaction (
    id BIGSERIAL PRIMARY KEY,
    sender_account_id BIGINT NOT NULL,
    receiver_account_id BIGINT NOT NULL,
    amount NUMERIC(19, 2) NOT NULL,
    idempotency_key UUID NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    CONSTRAINT fk_transaction_sender FOREIGN KEY (sender_account_id) REFERENCES tb_account(id),
    CONSTRAINT fk_transaction_receiver FOREIGN KEY (receiver_account_id) REFERENCES tb_account(id)
);
CREATE UNIQUE INDEX uk_transaction_idempotency ON tb_transaction(idempotency_key);
CREATE INDEX idx_transaction_sender ON tb_transaction(sender_account_id);
CREATE INDEX idx_transaction_receiver ON tb_transaction(receiver_account_id);
CREATE INDEX idx_transaction_created_at ON tb_transaction(created_at);

-- Tabela do Outbox
CREATE TABLE tb_outbox_event (
    id UUID PRIMARY KEY,
    aggregate_type VARCHAR(255) NOT NULL,
    aggregate_id VARCHAR(255) NOT NULL,
    event_type VARCHAR(255) NOT NULL,
    payload TEXT NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL
);


-- =================================================================
-- INSERÇÃO DE DADOS PARA TESTE DE CARGA
-- =================================================================

-- Inserir 100.000 usuários
INSERT INTO tb_user (name, document, email, status, role, created_at, updated_at)
SELECT
    'Test User ' || i,
    '999999' || LPAD(i::text, 6, '0'), -- Documento único
    'testuser' || i || '@astropay.com', -- Email único
    'ACTIVE',
    'ROLE_EMPLOYEE',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
FROM generate_series(1, 100000) as i;

-- Inserir 100.000 contas, uma para cada usuário criado
INSERT INTO tb_account (user_id, balance, status, version, created_at, updated_at)
SELECT
    i,
    10000.00, -- Saldo inicial para todas as contas
    'ACTIVE',
    0,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
FROM generate_series(1, 100000) as i;
