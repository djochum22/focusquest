//! FocusQuest as a Mac app: a window on FocusQuest and a menu bar icon. FocusQuest itself is the
//! backend serving the web app; this app finds it or starts it (`backend.rs`). Closing the window
//! keeps the app in the menu bar; quitting stops the copy it started, if any. While blocking is on,
//! it quits the apps FocusQuest blocks (`blocking.rs`, `apps.rs`) and sends Chrome tabs on blocked
//! websites to the blocked page (`websites.rs`, `chrome.rs`).

mod apps;
mod backend;
mod blocking;
mod chrome;
mod websites;

use std::collections::HashSet;
use std::sync::{Arc, Mutex};
use std::time::Duration;

use backend::{Backend, Found, Places};
use blocking::{Blocker, CurrentSession, DesktopState, Rules};
use chrome::ChromeStatus;
use tauri_plugin_autostart::ManagerExt;
use tauri_plugin_notification::NotificationExt;
use tauri::image::Image;
use tauri::menu::{CheckMenuItem, Menu, MenuItem, MenuItemKind};
use tauri::tray::TrayIconBuilder;
use tauri::{AppHandle, Manager, RunEvent, State, Url, WebviewUrl, WebviewWindowBuilder, WindowEvent};

const WINDOW: &str = "main";
/// How often to look at the running apps and Chrome's tabs, and how many looks between asking
/// FocusQuest again.
const LOOK_EVERY: Duration = Duration::from_secs(1);
const SYNC_EVERY_LOOKS: u32 = 3;

/// The copy of FocusQuest this app started; None if it uses one already running.
struct Started(Mutex<Option<Backend>>);

pub fn run() {
    let app = tauri::Builder::default()
        .plugin(tauri_plugin_notification::init())
        .plugin(tauri_plugin_autostart::Builder::new().build())
        .manage(Started(Mutex::new(None)))
        .invoke_handler(tauri::generate_handler![desktop_status, desktop_connect, desktop_disconnect, installed_apps])
        .setup(|app| {
            let token_path = app.path().app_data_dir()?.join("desktop-token");
            let blocker = Arc::new(Blocker::load(token_path));
            app.manage(blocker.clone());
            let handle = app.handle().clone();
            std::thread::spawn(move || keep_blocked_things_closed(&handle, &blocker));
            // The local page says what is happening until FocusQuest answers.
            WebviewWindowBuilder::new(app, WINDOW, WebviewUrl::App("index.html".into()))
                .title("FocusQuest")
                .inner_size(1200.0, 800.0)
                .min_inner_size(380.0, 480.0)
                .build()?;
            settle_open_at_login(app.handle());
            app.set_menu(app_menu(app.handle())?)?;
            menu_bar_icon(app.handle())?;
            let handle = app.handle().clone();
            std::thread::spawn(move || connect(&handle));
            Ok(())
        })
        .on_menu_event(|app, event| {
            if event.id().as_ref() == "reload" {
                if let Some(window) = app.get_webview_window(WINDOW) {
                    let _ = window.reload();
                }
            }
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

/// macOS's usual menus, with View, Reload (Cmd+R) added: the window loads the web app once, so after
/// FocusQuest is updated (Docker rebuilt) it would otherwise keep the old one until the app restarts.
fn app_menu(app: &AppHandle) -> tauri::Result<Menu<tauri::Wry>> {
    let menu = Menu::default(app)?;
    let reload = MenuItem::with_id(app, "reload", "Reload", true, Some("CmdOrCtrl+R"))?;
    for item in menu.items()? {
        if let MenuItemKind::Submenu(submenu) = item {
            if submenu.text()? == "View" {
                submenu.prepend(&reload)?;
            }
        }
    }
    Ok(menu)
}

fn menu_bar_icon(app: &AppHandle) -> tauri::Result<()> {
    let open = MenuItem::with_id(app, "open", "Open FocusQuest", true, None::<&str>)?;
    let at_login = app.autolaunch().is_enabled().unwrap_or(false);
    let login = CheckMenuItem::with_id(app, "login", "Open at Login", in_app_bundle(), at_login, None::<&str>)?;
    let quit = MenuItem::with_id(app, "quit", "Quit FocusQuest", true, None::<&str>)?;
    TrayIconBuilder::with_id("focusquest")
        .icon(Image::from_bytes(include_bytes!("../icons/tray.png"))?)
        .icon_as_template(true)
        .tooltip("FocusQuest")
        .menu(&Menu::with_items(app, &[&open, &login, &quit])?)
        .show_menu_on_left_click(true)
        .on_menu_event(move |app, event| match event.id().as_ref() {
            "open" => show_window(app),
            "login" => toggle_open_at_login(app, &login),
            "quit" => app.exit(0),
            _ => {}
        })
        .build(app)?;
    Ok(())
}

// What the web app calls inside the window (utils/desktopBridge.ts); allowed only from FocusQuest's own
// address (capabilities/web-app.json). The connection answers have the same shape as the browser
// extension's.

#[tauri::command]
fn desktop_status(blocker: State<'_, Arc<Blocker>>) -> DesktopState {
    blocker.state()
}

#[tauri::command]
async fn desktop_connect(token: String, blocker: State<'_, Arc<Blocker>>) -> Result<DesktopState, String> {
    let blocker = Arc::clone(&blocker);
    tauri::async_runtime::spawn_blocking(move || blocker.connect(token)).await.map_err(|e| e.to_string())
}

#[tauri::command]
fn desktop_disconnect(blocker: State<'_, Arc<Blocker>>) -> DesktopState {
    blocker.disconnect()
}

/// The installed apps, for the web app to suggest when adding a blocked app.
#[tauri::command]
async fn installed_apps(app: AppHandle) -> Result<Vec<apps::Installed>, String> {
    let home = app.path().home_dir().map_err(|e| e.to_string())?;
    tauri::async_runtime::spawn_blocking(move || apps::installed(&home)).await.map_err(|e| e.to_string())
}

/// Every second, quits the running apps FocusQuest blocks, saying why once per launch of each, and
/// closes blocked websites in Chrome.
fn keep_blocked_things_closed(app: &AppHandle, blocker: &Blocker) {
    let own_pid = std::process::id() as i32;
    let mut told: HashSet<i32> = HashSet::new();
    let mut looks = 0;
    loop {
        if looks % SYNC_EVERY_LOOKS == 0 {
            blocker.sync();
        }
        looks += 1;
        let rules = blocker.rules();
        let running = apps::running();
        let quitting = blocking::to_quit(&rules, &running, own_pid);
        told.retain(|pid| quitting.iter().any(|(app, _)| app.pid == *pid));
        for (running_app, name) in quitting {
            if told.insert(running_app.pid) {
                let _ = app.notification().builder().title("FocusQuest").body(blocking::reason(name, &rules)).show();
            }
            let pid = running_app.pid;
            let _ = app.run_on_main_thread(move || apps::quit(pid));
        }
        let chrome_running = running.iter().any(|app| app.bundle_id.as_deref() == Some(chrome::BUNDLE_ID));
        close_blocked_websites(app, blocker, &rules, chrome_running);
        std::thread::sleep(LOOK_EVERY);
    }
}

/// Sends each Chrome tab on a blocked website to FocusQuest's blocked page, saying why. Leaves Chrome
/// alone when it is not running (asking it anything would open it) or nothing is blocked.
fn close_blocked_websites(app: &AppHandle, blocker: &Blocker, rules: &Rules, chrome_running: bool) {
    if !chrome_running || !rules.active || rules.block.is_empty() {
        blocker.set_chrome_status(ChromeStatus::Idle);
        return;
    }
    let tabs = match chrome::tabs() {
        Ok(tabs) => tabs,
        Err(status) => return blocker.set_chrome_status(status),
    };
    let mut redirects = Vec::new();
    // Asked for once, and only when a tab is to be sent to the blocked page.
    let mut session: Option<Option<CurrentSession>> = None;
    for tab in &tabs {
        let Some(target) = websites::parse(&tab.url) else { continue };
        if let Some(rule) = websites::blocked_by(&target, &rules.block, &rules.allow) {
            let session = session.get_or_insert_with(|| blocker.current_session());
            redirects.push((tab, blocked_page(&rule.label(), rules, session.as_ref())));
            let _ = app.notification().builder().title("FocusQuest").body(blocking::reason(&rule.label(), rules)).show();
        }
    }
    blocker.set_chrome_status(match chrome::redirect(&redirects) {
        Ok(()) => ChromeStatus::Ok,
        Err(status) => status,
    });
}

/// The web app's page saying which site is blocked and why, with the session's time left and the
/// day's progress as they are now (`BlockedView.vue` counts down from there).
fn blocked_page(site: &str, rules: &Rules, session: Option<&CurrentSession>) -> String {
    let mut url = Url::parse(backend::URL).expect("a valid URL").join("blocked").expect("a valid path");
    let mut query = url.query_pairs_mut();
    query.append_pair("site", site).append_pair("reason", if rules.during_session { "session" } else { "target" });
    if let Some(session) = session {
        if session.session_id.is_some() {
            if let Some(status) = &session.status {
                query.append_pair("status", status);
            }
            if let Some(task) = &session.task_description {
                query.append_pair("task", task);
            }
            query.append_pair("remaining", &session.remaining_focus_seconds.to_string());
        }
        if let Some(streak) = &session.daily_streak {
            query.append_pair("done", &streak.qualifying_seconds.to_string()).append_pair("target", &streak.target_seconds.to_string());
        }
    }
    drop(query);
    url.into()
}

/// True when running as FocusQuest.app, not from `npm run dev`, which must not open at login.
fn in_app_bundle() -> bool {
    std::env::current_exe().is_ok_and(|exe| exe.to_string_lossy().contains(".app/Contents/MacOS/"))
}

/// Opens at login from the first run on, so blocking is on whenever the Mac is, unless the user has
/// switched it off in the menu bar. Enabled again on every run, so it follows the app if it moves
/// (say, into /Applications).
fn settle_open_at_login(app: &AppHandle) {
    if !in_app_bundle() {
        return;
    }
    let Ok(folder) = app.path().app_data_dir() else { return };
    let asked = folder.join("open-at-login-set");
    let autolaunch = app.autolaunch();
    if !asked.exists() || autolaunch.is_enabled().unwrap_or(false) {
        if let Err(error) = autolaunch.enable() {
            eprintln!("Cannot open FocusQuest at login: {error}");
            return;
        }
        let _ = std::fs::create_dir_all(&folder).and_then(|_| std::fs::write(&asked, ""));
    }
}

fn toggle_open_at_login(app: &AppHandle, item: &CheckMenuItem<tauri::Wry>) {
    let autolaunch = app.autolaunch();
    let result = if autolaunch.is_enabled().unwrap_or(false) { autolaunch.disable() } else { autolaunch.enable() };
    if let Err(error) = result {
        eprintln!("Cannot change opening at login: {error}");
    }
    let _ = item.set_checked(autolaunch.is_enabled().unwrap_or(false));
}

fn show_window(app: &AppHandle) {
    if let Some(window) = app.get_webview_window(WINDOW) {
        let _ = window.unminimize();
        let _ = window.show();
        let _ = window.set_focus();
    }
}
