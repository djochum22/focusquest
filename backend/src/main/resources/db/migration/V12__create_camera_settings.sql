-- The user's choice about camera verification. Consent is recorded as the version of the consent
-- text they accepted and when; both are null while camera verification is off. A consent to an
-- older version of the text does not count, so a changed text is always read before the camera runs.
CREATE TABLE camera_settings (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    consent_version INT,
    consented_at TIMESTAMP,
    verify_new_sessions BOOLEAN NOT NULL,
    CONSTRAINT fk_camera_settings_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT uq_camera_settings_user UNIQUE (user_id)
);
