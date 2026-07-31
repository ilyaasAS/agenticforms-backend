-- Module 3 - Formulaires & Champs
-- Flyway migration for: forms, form_fields

CREATE TABLE IF NOT EXISTS forms (
    id_form BIGINT NOT NULL AUTO_INCREMENT,
    workspace_id BIGINT NOT NULL,
    title VARCHAR(255) NOT NULL,
    description TEXT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    created_by BIGINT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id_form),
    CONSTRAINT fk_forms_workspace FOREIGN KEY (workspace_id) REFERENCES workspaces (id_workspace) ON DELETE CASCADE,
    CONSTRAINT fk_forms_created_by FOREIGN KEY (created_by) REFERENCES users (id_user) ON DELETE SET NULL
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS form_fields (
    id_form_field BIGINT NOT NULL AUTO_INCREMENT,
    form_id BIGINT NOT NULL,
    label VARCHAR(255) NOT NULL,
    field_type VARCHAR(32) NOT NULL,
    required TINYINT(1) NOT NULL DEFAULT 0,
    display_order INT NOT NULL DEFAULT 0,
    options_json TEXT NULL,
    placeholder VARCHAR(255) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id_form_field),
    CONSTRAINT fk_form_fields_form FOREIGN KEY (form_id) REFERENCES forms (id_form) ON DELETE CASCADE
) ENGINE=InnoDB;

SET @idx_forms_workspace := (
  SELECT COUNT(*) FROM information_schema.statistics
  WHERE table_schema = DATABASE()
    AND table_name = 'forms'
    AND index_name = 'idx_forms_workspace_id'
);
SET @sql := IF(@idx_forms_workspace = 0,
  'CREATE INDEX idx_forms_workspace_id ON forms(workspace_id)',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_forms_created_by := (
  SELECT COUNT(*) FROM information_schema.statistics
  WHERE table_schema = DATABASE()
    AND table_name = 'forms'
    AND index_name = 'idx_forms_created_by'
);
SET @sql := IF(@idx_forms_created_by = 0,
  'CREATE INDEX idx_forms_created_by ON forms(created_by)',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_form_fields_form := (
  SELECT COUNT(*) FROM information_schema.statistics
  WHERE table_schema = DATABASE()
    AND table_name = 'form_fields'
    AND index_name = 'idx_form_fields_form_id'
);
SET @sql := IF(@idx_form_fields_form = 0,
  'CREATE INDEX idx_form_fields_form_id ON form_fields(form_id)',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_form_fields_order := (
  SELECT COUNT(*) FROM information_schema.statistics
  WHERE table_schema = DATABASE()
    AND table_name = 'form_fields'
    AND index_name = 'idx_form_fields_form_order'
);
SET @sql := IF(@idx_form_fields_order = 0,
  'CREATE INDEX idx_form_fields_form_order ON form_fields(form_id, display_order)',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
