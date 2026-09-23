package com.example.focusquest.auth;

/**
 * Tells a client which entry screen to show before anyone has signed in.
 *
 * @param setupRequired true while no local user exists, i.e. the first-launch setup has not run yet
 */
public record SetupStatusResponse(boolean setupRequired) {
}
