
INSERT INTO tb_user (name, document, email, status, role, created_at, updated_at) VALUES
('John Doe', '12345678901', 'john.doe@example.com', 'ACTIVE', 'ROLE_EMPLOYEE', NOW(), NOW()),
('Jane Smith', '12345678902', 'jane.smith@example.com', 'ACTIVE', 'ROLE_MANAGER', NOW(), NOW());

INSERT INTO tb_account (user_id, balance, status, created_at, updated_at) VALUES
((SELECT id FROM tb_user WHERE email = 'john.doe@example.com'), 1000.00, 'ACTIVE', NOW(), NOW()),
((SELECT id FROM tb_user WHERE email = 'jane.smith@example.com'), 2500.00, 'ACTIVE', NOW(), NOW());
