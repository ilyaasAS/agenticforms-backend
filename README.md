# AgenticForms — backend

API REST du projet **AgenticForms** : création de compte, workspaces, formulaires, publication, soumissions publiques, admin, contact (MongoDB), intégrations (Stripe, Calendly, Google Calendar…).

Stack : **Java 21**, **Spring Boot 3.4**, Spring Security, JPA / MySQL, MongoDB, Flyway, JWT en cookie HttpOnly.

---

## Prérequis

- JDK 21
- Maven 3.9+ (ou le wrapper du projet s’il est présent)
- MySQL 8 et MongoDB 7 — le plus simple : les lancer via le dépôt **infra** (`docker compose up db mongo mailpit`)

En pratique, je lance presque toujours **toute la stack** depuis `agenticform-infra` plutôt que le backend tout seul.

---

## Lancer avec Docker (recommandé)

Depuis le dépôt infra :

```bash
docker compose up --build -d
```

L’API écoute sur **http://localhost:8080**.  
Health : `GET /actuator/health` (selon config).

---

## Lancer en local (hors image)

1. Avoir MySQL / Mongo accessibles (souvent via Compose).
2. Configurer les variables (même logique que `.env` de l’infra) : `DB_*`, `MONGO_URI`, `JWT_SECRET`, `CORS_ALLOWED_ORIGINS`, etc.
3. Profil Spring : `dev` ou `prod` selon le besoin.

```bash
./mvnw spring-boot:run
# ou
mvn spring-boot:run
```

---

## Organisation du code

```
src/main/java/com/agenticform/
  controller/     # endpoints REST
  service/        # règles métier
  repository/     # JPA / Mongo
  model/          # entités
  dto/            # requêtes / réponses
  security/       # JWT, CSRF, filtres
src/main/resources/
  db/migration/   # scripts Flyway (MySQL)
src/test/         # tests JUnit (~16 classes)
```

Persistance **hybride** :

- **MySQL** : comptes, workspaces, formulaires, champs, soumissions, etc.
- **MongoDB** : messages du formulaire Contact (inbox admin)

---

## Sécurité (aperçu)

- Auth JWT stockée en **cookie HttpOnly** (pas de token dans le localStorage)
- CSRF adapté SPA, CORS avec origines explicites (jamais `*`)
- Rate limiting sur les routes d’auth
- Mots de passe hashés (BCrypt)
- Profil `prod` : Flyway + `ddl-auto=validate`, cookies Secure si `COOKIE_FORCE_SECURE=true`

Les secrets ne doivent **jamais** être commités : ils viennent du `.env` / de l’environnement.

---

## Tests

```bash
mvn test
```

La CI GitHub du dépôt backend lance la suite JUnit.  
Le build Docker de l’image peut skipper les tests (Testcontainers) : les tests restent la référence en CI locale / GitHub.

---

## Endpoints utiles

| Zone | Exemple |
|------|---------|
| Auth | `/api/auth/...` |
| Formulaire public | `/api/v1/public/forms/{id}` |
| Soumission | `POST` public submit |
| Admin | routes protégées `ROLE_ADMIN` |

Le frontend consomme surtout `/api` (proxifié ou via `VITE_BACKEND_URL`).

---

## Notes pour la prod

Avant d’exposer l’API :

1. Remplacer tous les secrets (`JWT_SECRET`, DB, Mongo)
2. Activer HTTPS et `COOKIE_FORCE_SECURE=true`
3. Mettre à jour `CORS_ALLOWED_ORIGINS` avec le vrai domaine
4. Brancher un SMTP réel (Mailpit = dev uniquement)
5. Utiliser des clés reCAPTCHA de production

---

## Auteur

Ilyaas Abdoul Azis — backend AgenticForms · CDA / NEXA · 2025–2026.
