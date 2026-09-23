CREATE TABLE focus_sessions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    task_description VARCHAR(500),
    task_mode VARCHAR(20) NOT NULL,
    task_category VARCHAR(30) NOT NULL,
    planned_focus_minutes INT NOT NULL,
    active_focus_seconds BIGINT NOT NULL,
    finalized_paused_seconds BIGINT NOT NULL,
    qualifying_seconds BIGINT NOT NULL,
    overtime_seconds BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    blocking_state VARCHAR(20),
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    abandoned_at TIMESTAMP,
    override_used BOOLEAN NOT NULL,
    completion_xp_awarded BOOLEAN NOT NULL,
    created_at TIMESTAMP NOT NULL,
    active_segment_started_at TIMESTAMP,
    CONSTRAINT fk_focus_sessions_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE INDEX idx_focus_sessions_user_status ON focus_sessions (user_id, status);
CREATE INDEX idx_focus_sessions_user_started_at ON focus_sessions (user_id, started_at);

CREATE TABLE session_pauses (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id BIGINT NOT NULL,
    started_at TIMESTAMP NOT NULL,
    ended_at TIMESTAMP,
    duration_seconds BIGINT,
    finalized BOOLEAN NOT NULL,
    CONSTRAINT fk_session_pauses_session FOREIGN KEY (session_id) REFERENCES focus_sessions (id)
);

CREATE INDEX idx_session_pauses_session ON session_pauses (session_id);
