-- When the user's Chrome extension last checked in, whether or not a session was running. The
-- extension checks in every 30 seconds, so a recent value means it is alive. A session started or
-- resumed while it is alive is watched for interruptions from its first second, instead of only
-- after the extension's first check-in for that session.
ALTER TABLE users ADD COLUMN last_extension_heartbeat_at TIMESTAMP;
