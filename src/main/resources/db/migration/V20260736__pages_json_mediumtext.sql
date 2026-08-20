-- Capacité pages_json : TEXT (~64 Ko) trop petit pour médias inline / pages riches.
-- MEDIUMTEXT ≈ 16 Mo.

ALTER TABLE forms
    MODIFY COLUMN pages_json MEDIUMTEXT NULL;
