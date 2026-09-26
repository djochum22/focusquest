-- One revocable credential per user for the camera companion program, like the extension's. Only a
-- SHA-256 hash of the token is stored. last_seen_at is when the program last reported, so the app can
-- tell whether a camera-verified session is actually being checked.
CREATE TABLE companion_credentials (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    last_seen_at TIMESTAMP,
    CONSTRAINT fk_companion_credentials_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT uq_companion_credentials_user UNIQUE (user_id),
    CONSTRAINT uq_companion_credentials_hash UNIQUE (token_hash)
);
