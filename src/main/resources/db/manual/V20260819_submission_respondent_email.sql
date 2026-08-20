-- E-mail vérifié (page Connexion) lié à chaque soumission.
-- Dev : spring.jpa.hibernate.ddl-auto=update l’ajoute automatiquement.
ALTER TABLE form_submissions
    ADD COLUMN IF NOT EXISTS respondent_email VARCHAR(320) NULL;
