CREATE TABLE tb_user (
  id BIGINT PRIMARY KEY,
  name VARCHAR(255),
  document VARCHAR(255),
  email VARCHAR(255),
  status SMALLINT,
  role SMALLINT,
  created_at TIMESTAMP,
  updated_at TIMESTAMP
);

CREATE TABLE tb_account (
  id BIGINT PRIMARY KEY,
  user_id BIGINT,
  balance NUMERIC(19, 2),
  status SMALLINT,
  version BIGINT,
  created_at TIMESTAMP,
  updated_at TIMESTAMP
);

CREATE TABLE tb_transaction (
  id BIGINT PRIMARY KEY,
  sender_account_id BIGINT,
  receiver_account_id BIGINT,
  amount NUMERIC(19, 2),
  status SMALLINT,
  failure_reason VARCHAR(255),
  idempotency_key UUID,
  created_at TIMESTAMP
);

CREATE TABLE tb_outbox_event (
  id UUID PRIMARY KEY,
  aggregate_type VARCHAR(255),
  aggregate_id VARCHAR(255),
  event_type VARCHAR(255),
  payload TEXT,
  status SMALLINT,
  locked_at TIMESTAMP,
  retry_count INT,
  created_at TIMESTAMP
);

ALTER TABLE tb_account ADD FOREIGN KEY (user_id) REFERENCES tb_user(id);
ALTER TABLE tb_transaction ADD FOREIGN KEY (sender_account_id) REFERENCES tb_account(id);
ALTER TABLE tb_transaction ADD FOREIGN KEY (receiver_account_id) REFERENCES tb_account(id);
