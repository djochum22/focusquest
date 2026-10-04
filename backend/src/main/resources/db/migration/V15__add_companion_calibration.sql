-- Calibrating the camera companion program from Settings (C3b). The web app asks for a calibration;
-- the program's next answer tells it to start, and it reports back. Only when and whether it worked
-- are stored: the head angles stay on the user's computer.
ALTER TABLE companion_credentials ADD COLUMN calibration_requested_at TIMESTAMP;
ALTER TABLE companion_credentials ADD COLUMN calibration_started_at TIMESTAMP;
ALTER TABLE companion_credentials ADD COLUMN calibrated_at TIMESTAMP;
ALTER TABLE companion_credentials ADD COLUMN calibration_failure VARCHAR(300);
