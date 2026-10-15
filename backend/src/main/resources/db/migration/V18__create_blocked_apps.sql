-- Mac applications blocked by the desktop app on the same terms as blocked sites. An app is matched
-- by its bundle id when one is stored, and otherwise by its name, ignoring case.
CREATE TABLE blocked_apps (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    bundle_id VARCHAR(255),
    active BOOLEAN NOT NULL,
    CONSTRAINT fk_blocked_apps_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT uq_blocked_apps_user_name UNIQUE (user_id, name)
);

CREATE INDEX idx_blocked_apps_user_active ON blocked_apps (user_id, active);
