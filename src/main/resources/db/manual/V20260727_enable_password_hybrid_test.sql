-- Débloque les comptes de test hybrides (OAuth + login local).
-- Exécuter manuellement si besoin :
--   docker compose exec db mysql -u$DB_USER -p$DB_PASSWORD $DB_NAME < backend/src/main/resources/db/manual/V20260727_enable_password_hybrid_test.sql

UPDATE users
SET password_enabled = 1
WHERE email IN ('ilyaas.95.jv@gmail.com', 'test@gmail.com', 'test@example.com');
