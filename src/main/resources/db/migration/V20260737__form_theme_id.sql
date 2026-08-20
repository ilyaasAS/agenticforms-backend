-- Thème visuel du formulaire (light | dark | eco | charcoal) pour l’éditeur et le rendu public.
ALTER TABLE forms
    ADD COLUMN theme_id VARCHAR(32) NOT NULL DEFAULT 'dark';
