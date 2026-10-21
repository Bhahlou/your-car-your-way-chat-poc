-- Your Car Your Way - chat PoC: subset of the target schema (architecture proposal, §6.4).
-- Only the tables needed by the chat are created; their definitions are identical to the target schema.
-- Dates as TIMESTAMPTZ (UTC instant).

CREATE TYPE user_role           AS ENUM ('CLIENT', 'AGENT');
CREATE TYPE account_status      AS ENUM ('PENDING_VERIFICATION', 'ACTIVE', 'ANONYMIZED');
CREATE TYPE conversation_status AS ENUM ('WAITING', 'OPEN', 'CLOSED');

-- Accounts (accounts module): customers and agents ---------------------------
CREATE TABLE app_user (
  id               BIGSERIAL PRIMARY KEY,
  email            VARCHAR(255) NOT NULL UNIQUE,
  password_hash    VARCHAR(255) NOT NULL,                 -- {argon2}..., {bcrypt}... (see PasswordEncoder)
  role             user_role NOT NULL DEFAULT 'CLIENT',
  status           account_status NOT NULL DEFAULT 'PENDING_VERIFICATION',
  first_name       VARCHAR(100),
  last_name        VARCHAR(100),
  preferred_locale VARCHAR(10) NOT NULL DEFAULT 'en',
  created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Support (support module): conversations and messages -------------------------
CREATE TABLE conversation (
  id             BIGSERIAL PRIMARY KEY,
  client_id      BIGINT NOT NULL REFERENCES app_user(id),
  reservation_id BIGINT,                                  -- PoC: no reservation table, hence no foreign key
  agent_id       BIGINT REFERENCES app_user(id),          -- NULL until an agent claims it
  status         conversation_status NOT NULL DEFAULT 'WAITING',
  created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
  closed_at      TIMESTAMPTZ
);
-- Waiting queue (US-8.4): waiting conversations, oldest first
CREATE INDEX idx_conversation_queue ON conversation (created_at) WHERE status = 'WAITING';
CREATE INDEX idx_conversation_client ON conversation (client_id, created_at DESC);
-- A customer has only one unclosed conversation at a time
CREATE UNIQUE INDEX uq_conversation_client_active ON conversation (client_id) WHERE status <> 'CLOSED';

CREATE TABLE message (
  id              BIGSERIAL PRIMARY KEY,
  conversation_id BIGINT NOT NULL REFERENCES conversation(id) ON DELETE CASCADE,
  author_id       BIGINT NOT NULL REFERENCES app_user(id),
  content         TEXT NOT NULL CHECK (length(content) BETWEEN 1 AND 2000),
  sent_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_message_conversation ON message (conversation_id, id);
