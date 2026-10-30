-- Whether a session counts down from a planned length or is a stopwatch with no plan, in which case
-- its planned_focus_minutes is 0. Every session so far was a countdown.
ALTER TABLE focus_sessions ADD COLUMN timer_mode VARCHAR(20) DEFAULT 'COUNTDOWN' NOT NULL;
