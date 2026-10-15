fn main() {
    // The app's own commands, so capabilities/web-app.json can allow exactly these to the web app.
    tauri_build::try_build(tauri_build::Attributes::new().app_manifest(
        tauri_build::AppManifest::new().commands(&["desktop_status", "desktop_connect", "desktop_disconnect", "installed_apps"]),
    ))
    .expect("the Tauri build failed");
}
