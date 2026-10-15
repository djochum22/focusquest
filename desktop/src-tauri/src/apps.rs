//! The running apps, and quitting one, through macOS's NSWorkspace; and the installed apps, for the
//! web app to suggest when adding a blocked app.

use std::path::{Path, PathBuf};

use serde::Serialize;

use crate::blocking::Running;

/// An app the user can pick: its name as the Finder shows it, and its bundle id.
#[derive(Debug, Clone, PartialEq, Eq, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct Installed {
    pub name: String,
    pub bundle_id: String,
}

/// Where apps are installed, and how many folders deep to look (vendor folders such as
/// /Applications/Utilities or /Applications/Microsoft Office hold apps one level down).
fn app_folders(home: &Path) -> Vec<(PathBuf, usize)> {
    vec![
        (PathBuf::from("/Applications"), 2),
        (PathBuf::from("/System/Applications"), 2),
        (home.join("Applications"), 2),
    ]
}

/// The installed apps and the running ones with windows, each bundle id once, by name.
pub fn installed(home: &Path) -> Vec<Installed> {
    let mut found = Vec::new();
    for (folder, depth) in app_folders(home) {
        collect(&folder, depth, &mut found);
    }
    found.extend(running().into_iter().filter_map(|app| {
        Some(Installed { name: app.name?, bundle_id: app.bundle_id? })
    }));
    tidy(found)
}

fn collect(folder: &Path, depth: usize, found: &mut Vec<Installed>) {
    let Ok(entries) = std::fs::read_dir(folder) else { return };
    for entry in entries.flatten() {
        let path = entry.path();
        if path.extension().is_some_and(|extension| extension == "app") {
            if let (Some(name), Some(bundle_id)) = (path.file_stem().and_then(|stem| stem.to_str()), bundle_id(&path)) {
                found.push(Installed { name: name.to_string(), bundle_id });
            }
        } else if depth > 1 && path.is_dir() {
            collect(&path, depth - 1, found);
        }
    }
}

/// Each bundle id once (the first name found for it), sorted by name ignoring case.
fn tidy(mut found: Vec<Installed>) -> Vec<Installed> {
    let mut seen = std::collections::HashSet::new();
    found.retain(|app| seen.insert(app.bundle_id.to_lowercase()));
    found.sort_by_key(|app| app.name.to_lowercase());
    found
}

#[cfg(target_os = "macos")]
fn bundle_id(path: &Path) -> Option<String> {
    use objc2_foundation::{NSBundle, NSString};

    let bundle = NSBundle::bundleWithPath(&NSString::from_str(path.to_str()?))?;
    bundle.bundleIdentifier().map(|id| id.to_string())
}

#[cfg(not(target_os = "macos"))]
fn bundle_id(_path: &Path) -> Option<String> {
    None
}

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

#[cfg(test)]
mod tidy_tests {
    use super::*;

    #[test]
    fn each_bundle_id_once_sorted_by_name() {
        let app = |name: &str, id: &str| Installed { name: name.into(), bundle_id: id.into() };
        let tidied = tidy(vec![app("slack", "com.tinyspeck.slackmacgap"), app("Calculator", "com.apple.calculator"),
                               app("Slack", "com.tinyspeck.SlackMacGap")]);
        assert_eq!(tidied, vec![app("Calculator", "com.apple.calculator"), app("slack", "com.tinyspeck.slackmacgap")]);
    }
}

#[cfg(all(test, target_os = "macos"))]
mod tests {
    // Reads this Mac's running apps, so it is left out of the normal run: `cargo test -- --ignored`.
    #[test]
    #[ignore]
    fn lists_the_running_apps_with_their_bundle_ids() {
        let running = super::running();
        assert!(running.iter().any(|app| app.bundle_id.as_deref() == Some("com.apple.finder")), "{running:?}");
    }

    #[test]
    #[ignore]
    fn lists_the_installed_apps() {
        let home = std::env::var("HOME").unwrap();
        let installed = super::installed(std::path::Path::new(&home));
        assert!(installed.iter().any(|app| app.bundle_id == "com.apple.calculator" && app.name == "Calculator"), "{installed:?}");
    }
}
