package com.example.focusquest.blocking;

/** A blocked app as the web app, the desktop app and the backup see it. {@code bundleId} may be null. */
public record BlockedAppResponse(
        Long id,
        String name,
        String bundleId,
        boolean active
) {

    public static BlockedAppResponse from(BlockedApp app) {
        return new BlockedAppResponse(app.getId(), app.getName(), app.getBundleId(), app.isActive());
    }
}
