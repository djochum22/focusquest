package com.example.focusquest.vision;

/** What the camera can see that means the user is off task. It never judges productivity. */
public enum OffTaskSignal {
    /** Nobody in front of the camera. */
    AWAY,
    /** A phone visible in the user's hands. */
    PHONE,
    /** The user's head turned away from the category's work area. */
    LOOKING_AWAY
}
