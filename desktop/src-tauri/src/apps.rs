//! The running apps, and quitting one, through macOS's NSWorkspace.

use crate::blocking::Running;

#[cfg(target_os = "macos")]
pub fn running() -> Vec<Running> {
    use objc2_app_kit::{NSApplicationActivationPolicy, NSWorkspace};

    let workspace = NSWorkspace::sharedWorkspace();
    workspace
        .runningApplications()
        .iter()
        // Background-only processes have no windows to keep closed.
        .filter(|app| app.activationPolicy() != NSApplicationActivationPolicy::Prohibited)
        .map(|app| Running {
            pid: app.processIdentifier(),
            name: app.localizedName().map(|name| name.to_string()),
            bundle_id: app.bundleIdentifier().map(|id| id.to_string()),
        })
        .collect()
}

/// Asks the app to quit, as Cmd+Q does: it may still ask to save. Must run on the main thread.
#[cfg(target_os = "macos")]
pub fn quit(pid: i32) {
    use objc2_app_kit::NSRunningApplication;

    if let Some(app) = NSRunningApplication::runningApplicationWithProcessIdentifier(pid) {
        app.terminate();
    }
}

#[cfg(not(target_os = "macos"))]
pub fn running() -> Vec<Running> {
    Vec::new()
}

#[cfg(not(target_os = "macos"))]
pub fn quit(_pid: i32) {}

#[cfg(all(test, target_os = "macos"))]
mod tests {
    // Reads this Mac's running apps, so it is left out of the normal run: `cargo test -- --ignored`.
    #[test]
    #[ignore]
    fn lists_the_running_apps_with_their_bundle_ids() {
        let running = super::running();
        assert!(running.iter().any(|app| app.bundle_id.as_deref() == Some("com.apple.finder")), "{running:?}");
    }
}
