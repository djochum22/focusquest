//! FocusQuest as a Mac app: a window on FocusQuest and a menu bar icon. FocusQuest itself is the
//! backend serving the web app; this app finds it or starts it (`backend.rs`). Closing the window
//! keeps the app in the menu bar; quitting stops the copy it started, if any.

mod backend;

use std::sync::Mutex;

use backend::{Backend, Found, Places};
use tauri::image::Image;
use tauri::menu::{Menu, MenuItem};
use tauri::tray::TrayIconBuilder;
use tauri::{AppHandle, Manager, RunEvent, Url, WebviewUrl, WebviewWindowBuilder, WindowEvent};

const WINDOW: &str = "main";

/// The copy of FocusQuest this app started; None if it uses one already running.
struct Started(Mutex<Option<Backend>>);

pub fn run() {
    let app = tauri::Builder::default()
        .manage(Started(Mutex::new(None)))
        .setup(|app| {
            // The local page says what is happening until FocusQuest answers.
            WebviewWindowBuilder::new(app, WINDOW, WebviewUrl::App("index.html".into()))
                .title("FocusQuest")
                .inner_size(1200.0, 800.0)
                .min_inner_size(380.0, 480.0)
                .build()?;
            menu_bar_icon(app.handle())?;
            let handle = app.handle().clone();
            std::thread::spawn(move || connect(&handle));
            Ok(())
        })
        .on_window_event(|window, event| {
            if let WindowEvent::CloseRequested { api, .. } = event {
                api.prevent_close();
                let _ = window.hide();
            }
        })
        .build(tauri::generate_context!())
        .expect("FocusQuest could not start");

    app.run(|app, event| match event {
        RunEvent::Exit => {
            if let Some(mut backend) = app.state::<Started>().0.lock().unwrap().take() {
                backend.stop();
            }
        }
        #[cfg(target_os = "macos")]
        RunEvent::Reopen { .. } => show_window(app),
        _ => {}
    });
}

/// Finds FocusQuest or starts it, then opens it in the window; or says why it cannot.
fn connect(app: &AppHandle) {
    let result = match backend::probe() {
        Found::FocusQuest => Ok(()),
        Found::Other => Err(format!(
            "Another program is using {}, where FocusQuest runs. Stop it, then open FocusQuest again.",
            backend::ADDRESS
        )),
        Found::Nothing => {
            show_page(app, "starting", None);
            start(app)
        }
    };
    match result {
        Ok(()) => {
            if let Some(window) = app.get_webview_window(WINDOW) {
                let _ = window.navigate(Url::parse(backend::URL).expect("a valid URL"));
            }
        }
        Err(message) => show_page(app, "failed", Some(&message)),
    }
}

fn start(app: &AppHandle) -> Result<(), String> {
    let bundle = app.path().resource_dir().map_err(|e| e.to_string())?.join("backend");
    let home = app.path().home_dir().map_err(|e| e.to_string())?;
    let backend = Backend::start(&bundle, &Places::in_home(&home))?;
    *app.state::<Started>().0.lock().unwrap() = Some(backend);
    Ok(())
}

/// The local page in another state: it reads it from its address, so nothing is lost if it has not
/// loaded yet.
fn show_page(app: &AppHandle, state: &str, message: Option<&str>) {
    let Some(window) = app.get_webview_window(WINDOW) else { return };
    let Ok(mut url) = window.url() else { return };
    url.query_pairs_mut().clear().append_pair("state", state);
    if let Some(message) = message {
        url.query_pairs_mut().append_pair("message", message);
    }
    let _ = window.navigate(url);
}

fn menu_bar_icon(app: &AppHandle) -> tauri::Result<()> {
    let open = MenuItem::with_id(app, "open", "Open FocusQuest", true, None::<&str>)?;
    let quit = MenuItem::with_id(app, "quit", "Quit FocusQuest", true, None::<&str>)?;
    TrayIconBuilder::with_id("focusquest")
        .icon(Image::from_bytes(include_bytes!("../icons/tray.png"))?)
        .icon_as_template(true)
        .tooltip("FocusQuest")
        .menu(&Menu::with_items(app, &[&open, &quit])?)
        .show_menu_on_left_click(true)
        .on_menu_event(|app, event| match event.id().as_ref() {
            "open" => show_window(app),
            "quit" => app.exit(0),
            _ => {}
        })
        .build(app)?;
    Ok(())
}

fn show_window(app: &AppHandle) {
    if let Some(window) = app.get_webview_window(WINDOW) {
        let _ = window.unminimize();
        let _ = window.show();
        let _ = window.set_focus();
    }
}
