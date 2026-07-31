-- H-2 / M-4 : token_version utilisateurs + hash des reset tokens.
-- ddl-auto=update ajoute souvent les colonnes ; ce script nettoie l'ancien schéma.

-- 1) token_version (défaut 0)
SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'users'
    AND COLUMN_NAME = 'token_version'
);
SET @sql := IF(@col_exists = 0,
  'ALTER TABLE users ADD COLUMN token_version INT NOT NULL DEFAULT 0',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 2) password_reset_tokens : passer de token → token_hash (invalide les tokens en cours)
TRUNCATE TABLE password_reset_tokens;

SET @old_token := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'password_reset_tokens'
    AND COLUMN_NAME = 'token'
);
SET @sql := IF(@old_token > 0,
  'ALTER TABLE password_reset_tokens DROP INDEX uk_password_reset_token, DROP COLUMN token',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @hash_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'password_reset_tokens'
    AND COLUMN_NAME = 'token_hash'
);
SET @sql := IF(@hash_exists = 0,
  'ALTER TABLE password_reset_tokens ADD COLUMN token_hash VARCHAR(64) NOT NULL, ADD CONSTRAINT uk_password_reset_token_hash UNIQUE (token_hash)',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
