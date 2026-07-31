-- Field settings JSON (caption, default, layout, logic, validation)

ALTER TABLE form_fields
    ADD COLUMN settings_json TEXT NULL;
