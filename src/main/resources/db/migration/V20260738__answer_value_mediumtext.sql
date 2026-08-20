-- Audio / réponses riches : TEXT (~64 Ko) trop petit pour data URL d’enregistrement vocal.
-- MEDIUMTEXT ≈ 16 Mo.

ALTER TABLE form_submission_answers
    MODIFY COLUMN value_text MEDIUMTEXT NULL;

ALTER TABLE form_sessions
    MODIFY COLUMN answers_json MEDIUMTEXT NULL;
