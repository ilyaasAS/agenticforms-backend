-- Module 3b - Soumissions publiques de formulaires

CREATE TABLE IF NOT EXISTS form_submissions (
    id_submission BIGINT NOT NULL AUTO_INCREMENT,
    form_id BIGINT NOT NULL,
    submitted_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id_submission),
    CONSTRAINT fk_form_submissions_form FOREIGN KEY (form_id) REFERENCES forms (id_form) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS form_submission_answers (
    id_answer BIGINT NOT NULL AUTO_INCREMENT,
    submission_id BIGINT NOT NULL,
    field_id BIGINT NOT NULL,
    value_text TEXT NULL,
    PRIMARY KEY (id_answer),
    CONSTRAINT fk_submission_answers_submission FOREIGN KEY (submission_id)
        REFERENCES form_submissions (id_submission) ON DELETE CASCADE,
    CONSTRAINT fk_submission_answers_field FOREIGN KEY (field_id)
        REFERENCES form_fields (id_form_field) ON DELETE CASCADE
) ENGINE=InnoDB;

SET @idx_submissions_form := (
  SELECT COUNT(*) FROM information_schema.statistics
  WHERE table_schema = DATABASE()
    AND table_name = 'form_submissions'
    AND index_name = 'idx_form_submissions_form_id'
);
SET @sql := IF(@idx_submissions_form = 0,
  'CREATE INDEX idx_form_submissions_form_id ON form_submissions(form_id)',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_answers_submission := (
  SELECT COUNT(*) FROM information_schema.statistics
  WHERE table_schema = DATABASE()
    AND table_name = 'form_submission_answers'
    AND index_name = 'idx_submission_answers_submission_id'
);
SET @sql := IF(@idx_answers_submission = 0,
  'CREATE INDEX idx_submission_answers_submission_id ON form_submission_answers(submission_id)',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
