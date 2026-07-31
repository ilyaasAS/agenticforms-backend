-- Track public form views for simple analytics

SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'forms'
    AND column_name = 'view_count'
);
SET @sql := IF(@col_exists = 0,
  'ALTER TABLE forms ADD COLUMN view_count BIGINT NOT NULL DEFAULT 0',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
