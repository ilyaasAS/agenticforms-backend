-- Lier les sessions partielles à l'e-mail vérifié (reprise / nouvelle soumission)
ALTER TABLE form_sessions
    ADD COLUMN respondent_email VARCHAR(320) NULL AFTER status;

SET @idx_sessions_form_email := (
  SELECT COUNT(*) FROM information_schema.statistics
  WHERE table_schema = DATABASE()
    AND table_name = 'form_sessions'
    AND index_name = 'idx_form_sessions_form_email_status'
);
SET @sql := IF(@idx_sessions_form_email = 0,
  'CREATE INDEX idx_form_sessions_form_email_status ON form_sessions(form_id, respondent_email, status, updated_at)',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
