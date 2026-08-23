-- Brouillon (colonnes actuelles) vs version publique (snapshot figé au Publish).
ALTER TABLE forms
    ADD COLUMN published_snapshot_json LONGTEXT NULL,
    ADD COLUMN published_at DATETIME(6) NULL,
    ADD COLUMN has_unpublished_changes BIT(1) NOT NULL DEFAULT 1;

-- Formulaires déjà publiés : figer l’état actuel comme snapshot au prochain accès/publish.
UPDATE forms
SET has_unpublished_changes = 0
WHERE status = 'PUBLISHED';
