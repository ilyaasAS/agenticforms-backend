-- Pages document + UI component hint for catalog elements

ALTER TABLE forms
    ADD COLUMN pages_json TEXT NULL;

ALTER TABLE form_fields
    ADD COLUMN ui_component VARCHAR(64) NULL;
