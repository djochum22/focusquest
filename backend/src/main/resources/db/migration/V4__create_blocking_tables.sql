CREATE TABLE blocked_targets (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    target_type VARCHAR(20) NOT NULL,
    target_value VARCHAR(253) NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    active BOOLEAN NOT NULL,
    CONSTRAINT fk_blocked_targets_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT uq_blocked_targets_user_value UNIQUE (user_id, target_value)
);

CREATE INDEX idx_blocked_targets_user_active ON blocked_targets (user_id, active);

CREATE TABLE allowlist_targets (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    target_type VARCHAR(20) NOT NULL,
    target_value VARCHAR(253) NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    active BOOLEAN NOT NULL,
    CONSTRAINT fk_allowlist_targets_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT uq_allowlist_targets_user_value UNIQUE (user_id, target_value)
);

CREATE INDEX idx_allowlist_targets_user_active ON allowlist_targets (user_id, active);
