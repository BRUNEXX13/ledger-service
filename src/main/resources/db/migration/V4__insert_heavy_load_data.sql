-- Inserir 100.000 usuários, começando de onde o V3 parou
INSERT INTO tb_user (name, document, email, status, role, created_at, updated_at)
SELECT
    'Heavy User ' || i,
    'D' || LPAD(i::text, 11, '0'), -- Prefixo 'D' para garantir unicidade
    'heavyuser' || i || '@example.com',
    'ACTIVE',
    'ROLE_EMPLOYEE',
    NOW(),
    NOW()
FROM generate_series(10003, 110002) AS i
ON CONFLICT (document) DO NOTHING;

-- Inserir 100.000 contas
INSERT INTO tb_account (user_id, balance, status, created_at, updated_at)
SELECT
    id,
    (random() * 50000 + 1000)::numeric(10,2), -- Saldo aleatório entre 1.000 e 51.000
    'ACTIVE',
    NOW(),
    NOW()
FROM tb_user
WHERE id > 10002
ON CONFLICT (user_id) DO NOTHING;

-- Inserir 100.000 transações de forma segura
-- Seleciona IDs reais da tabela de contas para evitar erro de FK
INSERT INTO tb_transaction (sender_account_id, receiver_account_id, amount, idempotency_key, created_at)
SELECT
    s.id,
    r.id,
    (random() * 100)::numeric(10,2),
    gen_random_uuid(),
    NOW() - (row_number() over() * interval '1 second')
FROM
    (SELECT id FROM tb_account ORDER BY random() LIMIT 100000) s, -- 100k remetentes aleatórios
    (SELECT id FROM tb_account ORDER BY random() LIMIT 100000) r  -- 100k destinatários aleatórios
WHERE s.id != r.id -- Garante que não transfere para si mesmo
LIMIT 100000;
