-- Whether the user keeps their session history forever. Off by default: ended sessions that
-- started more than 30 days ago are deleted, along with their pauses and camera records.
ALTER TABLE users ADD COLUMN keep_history_forever BOOLEAN NOT NULL DEFAULT FALSE;
