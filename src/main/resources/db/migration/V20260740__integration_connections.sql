-- Connexions d’intégrations (Calendly OAuth, etc.)
CREATE TABLE IF NOT EXISTS integration_connections (
    id_integration_connection BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    provider VARCHAR(32) NOT NULL,
    access_token TEXT NOT NULL,
    refresh_token TEXT NULL,
    expires_at DATETIME(6) NULL,
    owner_uri VARCHAR(512) NULL,
    organization_uri VARCHAR(512) NULL,
    provider_email VARCHAR(255) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id_integration_connection),
    CONSTRAINT uk_integration_user_provider UNIQUE (user_id, provider),
    CONSTRAINT fk_integration_connections_user FOREIGN KEY (user_id) REFERENCES users (id_user)
        ON DELETE CASCADE
);
