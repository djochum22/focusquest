package com.example.focusquest.vision;

/** Where calibrating the companion program stands, as Settings shows it. */
public enum CalibrationState {
    /** The program has never reported a calibration. */
    NOT_CALIBRATED,
    /** Asked for; waiting for the program to pick it up at its next report. */
    REQUESTED,
    /** The program is guiding the user through it in its camera window. */
    CALIBRATING,
    CALIBRATED,
    FAILED
}
