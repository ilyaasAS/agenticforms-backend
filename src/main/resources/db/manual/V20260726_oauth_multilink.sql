-- Manual migration: multi-linking OAuth (UserOAuthAccount).
-- Run against MySQL before deploying with spring.jpa.hibernate.ddl-auto=validate.
-- Safe to re-run only if steps are adapted (IF NOT EXISTS / column checks).

-- 1) New accounts table
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

-- 2) Migrate legacy columns from users (if they still exist)
-- Ignore errors if columns were already dropped.
INSERT INTO user_oauth_accounts (user_id, provider, provider_subject, linked_at)
SELECT u.id_user, u.oauth_provider, u.oauth_subject, COALESCE(u.created_at, NOW(6))
FROM users u
WHERE u.oauth_provider IS NOT NULL
  AND u.oauth_subject IS NOT NULL
  AND u.oauth_provider <> ''
  AND u.oauth_subject <> ''
  AND NOT EXISTS (
      SELECT 1 FROM user_oauth_accounts a
      WHERE a.provider = u.oauth_provider AND a.provider_subject = u.oauth_subject
  );

-- 3) Drop legacy unique index + columns (MySQL 8)
-- Run only after verifying the INSERT above.
ALTER TABLE users DROP INDEX uk_users_oauth_identity;
ALTER TABLE users DROP COLUMN oauth_provider;
ALTER TABLE users DROP COLUMN oauth_subject;
