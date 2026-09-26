-- Camera verification of sessions (requirements specification, section 21).

-- Whether the session is checked by the camera, chosen when it is created, and the off-task time
-- already settled against it: subtracted from its active time when it is credited to the streak.
ALTER TABLE focus_sessions ADD COLUMN camera_verification BOOLEAN DEFAULT FALSE NOT NULL;
ALTER TABLE focus_sessions ADD COLUMN off_task_seconds BIGINT DEFAULT 0 NOT NULL;

-- What the camera observed: one continuous stretch of one off-task signal. The companion program
-- sends a stretch again with the same client_event_id to extend it; observed_until is the last
-- moment it saw the signal. No image is ever stored.
CREATE TABLE camera_observations (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id BIGINT NOT NULL,
    client_event_id VARCHAR(64) NOT NULL,
    signal_type VARCHAR(20) NOT NULL,
    confidence DOUBLE NOT NULL,
    started_at TIMESTAMP NOT NULL,
    observed_until TIMESTAMP NOT NULL,
    received_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_camera_observations_session FOREIGN KEY (session_id) REFERENCES focus_sessions (id),
    CONSTRAINT uq_camera_observations_event UNIQUE (session_id, client_event_id)
);

CREATE INDEX idx_camera_observations_session_start ON camera_observations (session_id, started_at);

-- Off-task time settled against a session: the part of an off-task episode that fell in one credited
-- stretch of active time. An episode that spans a pause is settled in two parts.
CREATE TABLE off_task_intervals (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id BIGINT NOT NULL,
    episode_started_at TIMESTAMP NOT NULL,
    warned_at TIMESTAMP NOT NULL,
    deduction_started_at TIMESTAMP NOT NULL,
    deduction_ended_at TIMESTAMP NOT NULL,
    deducted_seconds BIGINT NOT NULL,
    disputed BOOLEAN NOT NULL,
    CONSTRAINT fk_off_task_intervals_session FOREIGN KEY (session_id) REFERENCES focus_sessions (id)
);

CREATE INDEX idx_off_task_intervals_session ON off_task_intervals (session_id);

-- Episodes the user marked as inaccurate. An episode is known by when it started; a disputed one is
-- never subtracted, and any part already settled is given back.
CREATE TABLE off_task_disputes (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id BIGINT NOT NULL,
    episode_started_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_off_task_disputes_session FOREIGN KEY (session_id) REFERENCES focus_sessions (id),
    CONSTRAINT uq_off_task_disputes_episode UNIQUE (session_id, episode_started_at)
);
