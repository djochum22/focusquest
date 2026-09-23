-- How much of a session's time has already been credited to the streak. Time is credited when a
-- pause is resumed and again when the session ends; these columns make each credit cover only
-- the time since the previous one, so nothing is counted twice.
ALTER TABLE focus_sessions ADD COLUMN streak_credited_active_seconds BIGINT DEFAULT 0 NOT NULL;
ALTER TABLE focus_sessions ADD COLUMN streak_credited_paused_seconds BIGINT DEFAULT 0 NOT NULL;
