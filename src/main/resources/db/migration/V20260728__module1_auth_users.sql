-- Module 1 - Authentification & Utilisateurs (Phase 2 CRUD sans IA)
-- Flyway migration for:
-- users, user_oauth_accounts, password_reset_tokens, email_verification_tokens

CREATE TABLE IF NOT EXISTS users (
    id_user BIGINT NOT NULL AUTO_INCREMENT,
    email VARCHAR(255) NOT NULL,
    password VARCHAR(255) NOT NULL,
    full_name VARCHAR(255) NULL,
    password_enabled TINYINT(1) NOT NULL DEFAULT 1,
    token_version INT NOT NULL DEFAULT 0,
    email_verified TINYINT(1) NOT NULL DEFAULT 0,
    email_verified_at DATETIME(6) NULL,
    role VARCHAR(32) NOT NULL DEFAULT 'ROLE_USER',
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id_user),
    CONSTRAINT uk_users_email UNIQUE (email)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS user_oauth_accounts (
    id_oauth_account BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    provider VARCHAR(32) NOT NULL,
    provider_subject VARCHAR(255) NOT NULL,
    linked_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id_oauth_account),
    CONSTRAINT uk_oauth_provider_subject UNIQUE (provider, provider_subject),
    CONSTRAINT uk_oauth_user_provider UNIQUE (user_id, provider),
    CONSTRAINT fk_oauth_accounts_user FOREIGN KEY (user_id) REFERENCES users (id_user)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS password_reset_tokens (
    id_reset_token BIGINT NOT NULL AUTO_INCREMENT,
    token_hash VARCHAR(64) NOT NULL,
    user_id BIGINT NOT NULL,
    expiry_date DATETIME(6) NOT NULL,
    PRIMARY KEY (id_reset_token),
    CONSTRAINT uk_password_reset_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_password_reset_user FOREIGN KEY (user_id) REFERENCES users (id_user)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS email_verification_tokens (
    id_email_verification_token BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    used_at DATETIME(6) NULL,
    PRIMARY KEY (id_email_verification_token),
    CONSTRAINT uk_email_verification_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_email_verification_user FOREIGN KEY (user_id) REFERENCES users (id_user)
) ENGINE=InnoDB;

SET @idx_reset_user := (
  SELECT COUNT(*) FROM information_schema.statistics
  WHERE table_schema = DATABASE()
    AND table_name = 'password_reset_tokens'
    AND index_name = 'idx_password_reset_tokens_user_id'
);
SET @sql := IF(@idx_reset_user = 0,
  'CREATE INDEX idx_password_reset_tokens_user_id ON password_reset_tokens(user_id)',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_verify_user := (
  SELECT COUNT(*) FROM information_schema.statistics
  WHERE table_schema = DATABASE()
    AND table_name = 'email_verification_tokens'
    AND index_name = 'idx_email_verification_tokens_user_id'
);
SET @sql := IF(@idx_verify_user = 0,
  'CREATE INDEX idx_email_verification_tokens_user_id ON email_verification_tokens(user_id)',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
