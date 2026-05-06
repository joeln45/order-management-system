-- V4: Authentication tables.
--   users          credentials + role
--   refresh_tokens server-side record of long-lived refresh tokens,
--                  stored hashed so a DB leak can't be replayed
--
-- Seeding of the default operator + demo customer happens in Java
-- (AuthSeeder) so we can use BCryptPasswordEncoder. A SQL migration
-- has no way to hash passwords safely.

CREATE TABLE users (
    id             VARCHAR(36)   PRIMARY KEY,
    username       VARCHAR(100)  NOT NULL UNIQUE,
    password_hash  VARCHAR(100)  NOT NULL,
    role           VARCHAR(20)   NOT NULL,
    enabled        BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_users_role CHECK (role IN ('CUSTOMER', 'OPERATOR'))
);

CREATE INDEX idx_users_username ON users(username);

CREATE TABLE refresh_tokens (
    id          VARCHAR(36)   PRIMARY KEY,
    user_id     VARCHAR(36)   NOT NULL,
    token_hash  VARCHAR(128)  NOT NULL UNIQUE,
    expires_at  TIMESTAMP     NOT NULL,
    created_at  TIMESTAMP     NOT NULL,
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens(user_id);
CREATE INDEX idx_refresh_tokens_token_hash ON refresh_tokens(token_hash);

-- Link customers to users (optional; legacy demo customers stay unlinked).
ALTER TABLE customers ADD COLUMN user_id VARCHAR(36) NULL;
ALTER TABLE customers ADD CONSTRAINT uk_customers_user_id UNIQUE (user_id);
ALTER TABLE customers ADD CONSTRAINT fk_customers_user FOREIGN KEY (user_id) REFERENCES users(id);
