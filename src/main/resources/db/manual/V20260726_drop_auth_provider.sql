-- Drop obsolete users.auth_provider ; add password_enabled for unlink safety.
-- Run on MySQL after multi-link migration. Idempotent-ish for local re-runs.

-- 1) Add password_enabled if missing (default true for existing rows)
SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'users'
    AND COLUMN_NAME = 'password_enabled'
);
SET @sql := IF(
  @col_exists = 0,
  'ALTER TABLE users ADD COLUMN password_enabled TINYINT(1) NOT NULL DEFAULT 1',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 2) OAuth-only accounts (were not LOCAL) cannot use password login
SET @auth_col := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'users'
    AND COLUMN_NAME = 'auth_provider'
);
SET @sql := IF(
  @auth_col > 0,
  'UPDATE users SET password_enabled = 0 WHERE auth_provider IS NOT NULL AND auth_provider <> ''LOCAL''',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 3) Drop legacy column if still present
SET @sql := IF(
  @auth_col > 0,
  'ALTER TABLE users DROP COLUMN auth_provider',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
