-- PawConnect authentication extension for an existing MySQL 8 database.
-- Review in staging first. It is additive: legacy seed users remain verified and can log in by email.

ALTER TABLE users
    ADD COLUMN username VARCHAR(50) NULL UNIQUE,
    ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN email_verified BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN locked BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE users ADD CONSTRAINT uk_users_phone UNIQUE (phone);

CREATE TABLE auth_otps (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    email VARCHAR(254) NOT NULL,
    purpose VARCHAR(30) NOT NULL,
    code_hash VARCHAR(64) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    consumed_at DATETIME(6) NULL,
    invalidated_at DATETIME(6) NULL,
    attempt_count INT NOT NULL DEFAULT 0,
    last_sent_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_auth_otps_user FOREIGN KEY (user_id) REFERENCES users(id),
    INDEX idx_auth_otps_active_lookup (user_id, purpose, created_at)
);

CREATE TABLE password_reset_sessions (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    consumed_at DATETIME(6) NULL,
    CONSTRAINT fk_password_reset_sessions_user FOREIGN KEY (user_id) REFERENCES users(id),
    INDEX idx_password_reset_sessions_token (token_hash)
);
