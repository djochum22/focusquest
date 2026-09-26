package com.example.focusquest.vision;

/** Where the user may look and still be on task. Looking anywhere else, for long enough, is looking away. */
public enum WorkArea {
    /** The screen only. */
    SCREEN,
    /** The screen, or down at the desk: reading or writing on paper. */
    SCREEN_OR_DESK,
    /** Anywhere: looking away is not checked, only being away and using a phone. */
    ANYWHERE
}
