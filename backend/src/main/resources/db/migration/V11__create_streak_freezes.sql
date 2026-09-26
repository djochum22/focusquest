-- Streak freezes the user has bought with gems. One row per freeze: unused while used_period_id is
-- null, spent once it points at the daily period it covered. A period can be covered only once.
CREATE TABLE streak_freezes (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    purchased_at TIMESTAMP NOT NULL,
    used_period_id BIGINT,
    used_at TIMESTAMP,
    CONSTRAINT fk_streak_freezes_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_streak_freezes_period FOREIGN KEY (used_period_id) REFERENCES streak_periods (id),
    CONSTRAINT uq_streak_freezes_used_period UNIQUE (used_period_id)
);

CREATE INDEX idx_streak_freezes_user ON streak_freezes (user_id);
