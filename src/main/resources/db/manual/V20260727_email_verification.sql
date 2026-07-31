-- E-1 / E-3 : email_verified + table email_verification_tokens
-- Rétrocompatibilité : comptes existants marqués vérifiés.

SET @col_ev := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'users'
    AND COLUMN_NAME = 'email_verified'
);
SET @sql := IF(@col_ev = 0,
  'ALTER TABLE users ADD COLUMN email_verified TINYINT(1) NOT NULL DEFAULT 0, ADD COLUMN email_verified_at DATETIME(6) NULL',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE users
SET email_verified = 1,
    email_verified_at = COALESCE(email_verified_at, UTC_TIMESTAMP())
WHERE email_verified = 0;

CREATE TABLE IF NOT EXISTS email_verification_tokens (
  id_email_verification_token BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  token_hash VARCHAR(64) NOT NULL,
  expires_at DATETIME(6) NOT NULL,
  created_at DATETIME(6) NOT NULL,
  used_at DATETIME(6) NULL,
  PRIMARY KEY (id_email_verification_token),
  UNIQUE KEY uk_email_verification_token_hash (token_hash),
  CONSTRAINT fk_email_verification_user FOREIGN KEY (user_id) REFERENCES users (id_user)
) ENGINE=InnoDB;
