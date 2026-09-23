package com.example.focusquest.blocking;

/** @param stateVersion the {@code stateVersion} the extension last synchronized; null if it has none */
public record HeartbeatRequest(String stateVersion) {
}
