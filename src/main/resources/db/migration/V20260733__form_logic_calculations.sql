-- Étape C — Moteur de logique & calculs (document JSON sur forms)

ALTER TABLE forms
    ADD COLUMN logic_rules_json TEXT NULL,
    ADD COLUMN calculations_json TEXT NULL;
