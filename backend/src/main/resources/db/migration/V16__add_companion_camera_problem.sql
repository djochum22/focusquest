-- Saying in the web app when the camera cannot be used (C2). The companion program sends why with each
-- report while it cannot use the camera, and a report without it clears it.
ALTER TABLE companion_credentials ADD COLUMN camera_problem VARCHAR(300);
