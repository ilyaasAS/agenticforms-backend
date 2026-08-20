-- Soft-delete des champs : conserve les réponses historiques en Résultats.
ALTER TABLE form_fields
    ADD COLUMN deleted_at DATETIME(6) NULL AFTER updated_at;

SET @idx_fields_deleted := (
  SELECT COUNT(*) FROM information_schema.statistics
  WHERE table_schema = DATABASE()
    AND table_name = 'form_fields'
    AND index_name = 'idx_form_fields_form_deleted'
);
SET @sql := IF(@idx_fields_deleted = 0,
  'CREATE INDEX idx_form_fields_form_deleted ON form_fields(form_id, deleted_at)',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
