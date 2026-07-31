-- Module 2 - Workspaces & Membres
-- Flyway migration for: workspaces, workspace_members

CREATE TABLE IF NOT EXISTS workspaces (
    id_workspace BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(255) NOT NULL,
    slug VARCHAR(255) NOT NULL,
    description TEXT NULL,
    owner_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id_workspace),
    CONSTRAINT uk_workspaces_slug UNIQUE (slug),
    CONSTRAINT fk_workspaces_owner FOREIGN KEY (owner_id) REFERENCES users (id_user)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS workspace_members (
    id_workspace_member BIGINT NOT NULL AUTO_INCREMENT,
    workspace_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    role VARCHAR(32) NOT NULL,
    joined_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id_workspace_member),
    CONSTRAINT uk_workspace_members_workspace_user UNIQUE (workspace_id, user_id),
    CONSTRAINT fk_workspace_members_workspace FOREIGN KEY (workspace_id) REFERENCES workspaces (id_workspace),
    CONSTRAINT fk_workspace_members_user FOREIGN KEY (user_id) REFERENCES users (id_user)
) ENGINE=InnoDB;

SET @idx_workspaces_owner := (
  SELECT COUNT(*) FROM information_schema.statistics
  WHERE table_schema = DATABASE()
    AND table_name = 'workspaces'
    AND index_name = 'idx_workspaces_owner_id'
);
SET @sql := IF(@idx_workspaces_owner = 0,
  'CREATE INDEX idx_workspaces_owner_id ON workspaces(owner_id)',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_members_workspace := (
  SELECT COUNT(*) FROM information_schema.statistics
  WHERE table_schema = DATABASE()
    AND table_name = 'workspace_members'
    AND index_name = 'idx_workspace_members_workspace_id'
);
SET @sql := IF(@idx_members_workspace = 0,
  'CREATE INDEX idx_workspace_members_workspace_id ON workspace_members(workspace_id)',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_members_user := (
  SELECT COUNT(*) FROM information_schema.statistics
  WHERE table_schema = DATABASE()
    AND table_name = 'workspace_members'
    AND index_name = 'idx_workspace_members_user_id'
);
SET @sql := IF(@idx_members_user = 0,
  'CREATE INDEX idx_workspace_members_user_id ON workspace_members(user_id)',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
