package com.example.focusquest.vision;

/** Where a session stands with the camera right now. */
public enum OffTaskState {
    /** The camera does not check this session. */
    NOT_VERIFIED,
    /** The session is not running (planned, paused or ended), so nothing is being checked. */
    NOT_RUNNING,
    /** No off-task signal is being seen, or the episode going on was disputed. */
    ON_TASK,
    /** An off-task signal is being seen, not yet for long enough to warn. */
    OFF_TASK,
    /** The user has been warned; the grace period is running. */
    WARNED,
    /** The grace period is over: the time is being subtracted. */
    DEDUCTING
}
