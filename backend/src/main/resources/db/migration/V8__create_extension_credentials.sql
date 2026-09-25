-- One long-lived, revocable credential per user for the Chrome extension. Only a SHA-256 hash of the
-- token is stored, so a copy of the database cannot be used to call the API.
CREATE TABLE extension_credentials (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_extension_credentials_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT uq_extension_credentials_user UNIQUE (user_id),
    CONSTRAINT uq_extension_credentials_hash UNIQUE (token_hash)
);
