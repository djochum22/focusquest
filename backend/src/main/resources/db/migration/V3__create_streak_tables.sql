CREATE TABLE streak_configurations (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    period_type VARCHAR(20) NOT NULL,
    target_minutes INT NOT NULL,
    required_task_mode VARCHAR(20) NOT NULL,
    required_category VARCHAR(30),
    effective_from TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_streak_configurations_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE INDEX idx_streak_configurations_user_period_effective
    ON streak_configurations (user_id, period_type, effective_from);

CREATE TABLE streak_periods (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    configuration_snapshot_id BIGINT NOT NULL,
    period_type VARCHAR(20) NOT NULL,
    start_time TIMESTAMP NOT NULL,
    end_time TIMESTAMP NOT NULL,
    target_minutes INT NOT NULL,
    required_task_mode VARCHAR(20) NOT NULL,
    required_category VARCHAR(30),
    qualifying_seconds BIGINT NOT NULL,
    overtime_seconds BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    freeze_consumed BOOLEAN NOT NULL,
    completed_at TIMESTAMP,
    CONSTRAINT fk_streak_periods_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_streak_periods_configuration FOREIGN KEY (configuration_snapshot_id) REFERENCES streak_configurations (id)
);

CREATE UNIQUE INDEX idx_streak_periods_user_period_start ON streak_periods (user_id, period_type, start_time);

CREATE TABLE streak_contributions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    streak_period_id BIGINT NOT NULL,
    session_id BIGINT NOT NULL,
    active_seconds BIGINT NOT NULL,
    paused_seconds BIGINT NOT NULL,
    qualifying_seconds BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_streak_contributions_period FOREIGN KEY (streak_period_id) REFERENCES streak_periods (id),
    CONSTRAINT fk_streak_contributions_session FOREIGN KEY (session_id) REFERENCES focus_sessions (id)
);

CREATE INDEX idx_streak_contributions_period ON streak_contributions (streak_period_id);
