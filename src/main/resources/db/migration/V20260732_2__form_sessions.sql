-- Partial respondent sessions (in-progress / abandon tracking)
-- MySQL: JSON column (équivalent JSONB Postgres)

CREATE TABLE IF NOT EXISTS form_sessions (
    session_id VARCHAR(64) NOT NULL,
    form_id BIGINT NOT NULL,
    last_field_id BIGINT NULL,
    answers_json JSON NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'IN_PROGRESS',
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (session_id),
    CONSTRAINT fk_form_sessions_form FOREIGN KEY (form_id) REFERENCES forms (id_form) ON DELETE CASCADE,
    CONSTRAINT fk_form_sessions_last_field FOREIGN KEY (last_field_id)
        REFERENCES form_fields (id_form_field) ON DELETE SET NULL
) ENGINE=InnoDB;

SET @idx_sessions_form_status := (
  SELECT COUNT(*) FROM information_schema.statistics
  WHERE table_schema = DATABASE()
    AND table_name = 'form_sessions'
    AND index_name = 'idx_form_sessions_form_status'
);
SET @sql := IF(@idx_sessions_form_status = 0,
  'CREATE INDEX idx_form_sessions_form_status ON form_sessions(form_id, status, updated_at)',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
