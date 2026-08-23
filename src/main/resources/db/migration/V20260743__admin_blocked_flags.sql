-- Centre de commande admin : bloquer un compte ou un formulaire public.
ALTER TABLE users
    ADD COLUMN blocked BIT(1) NOT NULL DEFAULT 0;

ALTER TABLE forms
    ADD COLUMN blocked BIT(1) NOT NULL DEFAULT 0;
