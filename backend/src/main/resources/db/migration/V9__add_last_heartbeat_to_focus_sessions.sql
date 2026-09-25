-- When the Chrome extension last checked in while this session was running. A gap longer than the
-- heartbeat timeout means the session could not be verified (browser closed, extension broken,
-- backend down), so the session is interrupted and the unverified time after this instant is
-- dropped. Null until the extension first checks in, so a session run without the extension is
-- never interrupted.
ALTER TABLE focus_sessions ADD COLUMN last_heartbeat_at TIMESTAMP;
