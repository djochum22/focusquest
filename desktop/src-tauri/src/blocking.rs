//! What to block: FocusQuest's blocked apps (quit while running) and websites (Chrome tabs sent to
//! the blocked page, `chrome.rs`) while blocking is on.
//!
//! The app reads `GET /api/extension/blocking-state` with its own desktop token (`fqd_`), which the
//! web app hands it inside the app's window, and only that route accepts it. Blocking follows the same
//! rule as websites: on until the day's focus target is reached, and during any session. A blocked app
//! is matched by bundle id when it has one, otherwise by name ignoring case, as the backend does. If
//! FocusQuest cannot be reached, the last answer stands, as in the browser extension.

use std::fs;
use std::io::Write;
use std::path::PathBuf;
use std::sync::Mutex;
use std::time::Duration;

use serde::{Deserialize, Serialize};

use crate::backend;
use crate::chrome::ChromeStatus;
use crate::websites::UrlRule;

/// Never quit, whatever the list says: this app, and the parts of macOS it cannot do without.
const NEVER_QUIT: &[&str] = &[
    "com.focusquest.desktop",
    "com.apple.finder",
    "com.apple.dock",
    "com.apple.loginwindow",
    "com.apple.systemuiserver",
];

#[derive(Debug, Clone, PartialEq, Eq, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct BlockedApp {
    pub name: String,
    pub bundle_id: Option<String>,
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
struct BlockingState {
    enforcement_active: bool,
    session_id: Option<i64>,
    #[serde(default)]
    blocked_apps: Vec<BlockedApp>,
    #[serde(default)]
    block_rules: Vec<UrlRule>,
    #[serde(default)]
    allow_rules: Vec<UrlRule>,
}

/// What the blocked page shows: `GET /api/extension/current-session`. The session fields are empty
/// when blocking comes from the day's unmet focus target alone.
#[derive(Debug, Clone, Default, PartialEq, Eq, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct CurrentSession {
    pub session_id: Option<i64>,
    pub status: Option<String>,
    pub task_description: Option<String>,
    #[serde(default)]
    pub remaining_focus_seconds: i64,
    pub daily_streak: Option<DailyStreak>,
}

#[derive(Debug, Clone, PartialEq, Eq, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct DailyStreak {
    pub qualifying_seconds: i64,
    pub target_seconds: i64,
}

/// What to block, from the last answer.
#[derive(Debug, Clone, Default, PartialEq, Eq)]
pub struct Rules {
    pub active: bool,
    /// Blocking because a session is running, rather than for the day's target alone.
    pub during_session: bool,
    pub apps: Vec<BlockedApp>,
    pub block: Vec<UrlRule>,
    pub allow: Vec<UrlRule>,
}

/// As the browser extension reports its own (`ExtensionState` in the web app's `extensionBridge.ts`).
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize)]
#[serde(rename_all = "kebab-case")]
pub enum Status {
    NeverSynced,
    Ok,
    Unauthorized,
    Offline,
    Error,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct DesktopState {
    pub has_token: bool,
    pub status: Status,
    /// Whether it could look at Chrome's tabs last time it tried.
    pub chrome: ChromeStatus,
}

/// A running app, as macOS lists it.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Running {
    pub pid: i32,
    pub name: Option<String>,
    pub bundle_id: Option<String>,
}

struct Connection {
    token: Option<String>,
    status: Status,
    rules: Rules,
}

/// The token, what FocusQuest last said, and how it went.
pub struct Blocker {
    token_path: PathBuf,
    connection: Mutex<Connection>,
    chrome: Mutex<ChromeStatus>,
}

impl Blocker {
    /// With the token saved at `token_path`, if any.
    pub fn load(token_path: PathBuf) -> Blocker {
        let token = fs::read_to_string(&token_path).ok().map(|t| t.trim().to_string()).filter(|t| !t.is_empty());
        Blocker {
            token_path,
            connection: Mutex::new(Connection { token, status: Status::NeverSynced, rules: Rules::default() }),
            chrome: Mutex::new(ChromeStatus::Idle),
        }
    }

    pub fn state(&self) -> DesktopState {
        let connection = self.connection.lock().unwrap();
        DesktopState { has_token: connection.token.is_some(), status: connection.status, chrome: self.chrome_status() }
    }

    pub fn chrome_status(&self) -> ChromeStatus {
        *self.chrome.lock().unwrap()
    }

    pub fn set_chrome_status(&self, status: ChromeStatus) {
        *self.chrome.lock().unwrap() = status;
    }

    pub fn rules(&self) -> Rules {
        self.connection.lock().unwrap().rules.clone()
    }

    /// Takes a new token and tries it straight away; kept only if FocusQuest accepts it.
    pub fn connect(&self, token: String) -> DesktopState {
        let (status, rules) = fetch(&token);
        let mut connection = self.connection.lock().unwrap();
        connection.status = status;
        if status == Status::Unauthorized {
            return DesktopState { has_token: connection.token.is_some(), status, chrome: self.chrome_status() };
        }
        if let Err(error) = save_private(&self.token_path, &token) {
            eprintln!("Cannot save the desktop token to {}: {error}", self.token_path.display());
        }
        connection.token = Some(token);
        if let Some(rules) = rules {
            connection.rules = rules;
        }
        DesktopState { has_token: true, status, chrome: self.chrome_status() }
    }

    /// Forgets the token: nothing is blocked until the web app connects the app again.
    pub fn disconnect(&self) -> DesktopState {
        let _ = fs::remove_file(&self.token_path);
        let mut connection = self.connection.lock().unwrap();
        *connection = Connection { token: None, status: Status::NeverSynced, rules: Rules::default() };
        DesktopState { has_token: false, status: connection.status, chrome: self.chrome_status() }
    }

    /// The session and the day's progress, for the blocked page; None if FocusQuest cannot say.
    pub fn current_session(&self) -> Option<CurrentSession> {
        let token = self.connection.lock().unwrap().token.clone()?;
        let response = ureq::get(&format!("{}api/extension/current-session", backend::URL))
            .set("Authorization", &format!("Bearer {token}"))
            .timeout(Duration::from_secs(2))
            .call()
            .ok()?;
        if response.status() != 200 {
            return None; // 204: nothing is enforced any more
        }
        response.into_json().ok()
    }

    /// Asks FocusQuest again. A refused token ends blocking; an unreachable FocusQuest does not.
    pub fn sync(&self) {
        let Some(token) = self.connection.lock().unwrap().token.clone() else { return };
        let (status, rules) = fetch(&token);
        let mut connection = self.connection.lock().unwrap();
        if connection.token.as_deref() != Some(token.as_str()) {
            return; // connected again, or disconnected, meanwhile
        }
        connection.status = status;
        match (status, rules) {
            (_, Some(rules)) => connection.rules = rules,
            (Status::Unauthorized, None) => connection.rules = Rules::default(),
            _ => {}
        }
    }
}

fn fetch(token: &str) -> (Status, Option<Rules>) {
    let response = ureq::get(&format!("{}api/extension/blocking-state", backend::URL))
        .set("Authorization", &format!("Bearer {token}"))
        .timeout(Duration::from_secs(5))
        .call();
    match response {
        Ok(response) => match response.into_json::<BlockingState>() {
            Ok(state) => (Status::Ok, Some(rules_from(state))),
            Err(_) => (Status::Error, None),
        },
        Err(ureq::Error::Status(401 | 403, _)) => (Status::Unauthorized, None),
        Err(ureq::Error::Status(_, _)) => (Status::Error, None),
        Err(ureq::Error::Transport(_)) => (Status::Offline, None),
    }
}

fn rules_from(state: BlockingState) -> Rules {
    if !state.enforcement_active {
        return Rules::default();
    }
    Rules {
        active: true,
        during_session: state.session_id.is_some(),
        apps: state.blocked_apps,
        block: state.block_rules,
        allow: state.allow_rules,
    }
}

fn save_private(path: &PathBuf, contents: &str) -> std::io::Result<()> {
    use std::os::unix::fs::OpenOptionsExt;
    if let Some(folder) = path.parent() {
        fs::create_dir_all(folder)?;
    }
    let mut file = fs::OpenOptions::new().write(true).create(true).truncate(true).mode(0o600).open(path)?;
    file.write_all(contents.as_bytes())
}

/// The running apps to quit under these rules, never this app nor the parts of macOS in [`NEVER_QUIT`].
pub fn to_quit<'a>(rules: &Rules, running: &'a [Running], own_pid: i32) -> Vec<(&'a Running, &'a str)> {
    if !rules.active {
        return Vec::new();
    }
    running
        .iter()
        .filter(|app| app.pid != own_pid)
        .filter(|app| !app.bundle_id.as_deref().is_some_and(|id| NEVER_QUIT.iter().any(|never| never.eq_ignore_ascii_case(id))))
        .filter_map(|app| {
            rules.apps.iter().find(|blocked| is_blocked(blocked, app)).map(|_| (app, app.name.as_deref().unwrap_or("An app")))
        })
        .collect()
}

fn is_blocked(blocked: &BlockedApp, running: &Running) -> bool {
    match &blocked.bundle_id {
        Some(bundle_id) => running.bundle_id.as_deref().is_some_and(|id| id.eq_ignore_ascii_case(bundle_id)),
        None => running.name.as_deref().is_some_and(|name| name.to_lowercase() == blocked.name.to_lowercase()),
    }
}

/// What the notification says when an app is quit or a website closed.
pub fn reason(name: &str, rules: &Rules) -> String {
    if rules.during_session {
        format!("{name} is blocked during your focus session.")
    } else {
        format!("{name} is blocked until you reach today's focus target.")
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn app(pid: i32, name: &str, bundle_id: Option<&str>) -> Running {
        Running { pid, name: Some(name.into()), bundle_id: bundle_id.map(Into::into) }
    }

    fn blocking(apps: &[(&str, Option<&str>)]) -> Rules {
        Rules {
            active: true,
            during_session: false,
            apps: apps.iter().map(|(name, id)| BlockedApp { name: (*name).into(), bundle_id: id.map(Into::into) }).collect(),
            ..Rules::default()
        }
    }

    #[test]
    fn matched_by_bundle_id_when_it_has_one_otherwise_by_name_ignoring_case() {
        let running = [
            app(1, "Slack", Some("com.tinyspeck.slackmacgap")),
            app(2, "Steam", Some("com.valvesoftware.steam")),
            app(3, "Notes", Some("com.apple.Notes")),
        ];
        let rules = blocking(&[("Anything", Some("com.tinyspeck.slackmacgap")), ("steam", None)]);
        let pids: Vec<i32> = to_quit(&rules, &running, 99).iter().map(|(app, _)| app.pid).collect();
        assert_eq!(pids, vec![1, 2]);
    }

    #[test]
    fn a_name_does_not_match_when_a_bundle_id_is_given() {
        let rules = blocking(&[("Slack", Some("com.tinyspeck.slackmacgap"))]);
        assert!(to_quit(&rules, &[app(1, "Slack", Some("com.other.slack"))], 99).is_empty());
    }

    #[test]
    fn nothing_is_quit_while_blocking_is_off() {
        let mut rules = blocking(&[("Steam", None)]);
        rules.active = false;
        assert!(to_quit(&rules, &[app(1, "Steam", None)], 99).is_empty());
    }

    #[test]
    fn never_this_app_nor_the_finder() {
        let rules = blocking(&[("FocusQuest", None), ("Finder", None), ("focusquest-desktop", None)]);
        let running = [
            app(1, "FocusQuest", Some("com.focusquest.desktop")),
            app(2, "Finder", Some("com.apple.finder")),
            app(3, "focusquest-desktop", None),
        ];
        assert!(to_quit(&rules, &running, 3).is_empty());
    }

    #[test]
    fn the_answer_becomes_rules() {
        let state: BlockingState = serde_json::from_str(
            r#"{"enforcementActive":true,"sessionId":7,"blockedApps":[{"id":1,"name":"Steam","bundleId":null,"active":true}],
                "blockRules":[{"id":2,"targetType":"DOMAIN","targetValue":"youtube.com","host":"youtube.com","path":null,"displayName":null}],
                "allowRules":[]}"#,
        )
        .unwrap();
        let rules = rules_from(state);
        assert!(rules.active && rules.during_session);
        assert_eq!(rules.apps, vec![BlockedApp { name: "Steam".into(), bundle_id: None }]);
        assert_eq!(rules.block, vec![UrlRule { host: "youtube.com".into(), path: String::new() }]);
        assert!(rules.allow.is_empty());
        assert_eq!(reason("Steam", &rules), "Steam is blocked during your focus session.");
    }

    #[test]
    fn the_current_session_is_read_as_focusquest_sends_it() {
        let session: CurrentSession = serde_json::from_str(
            r#"{"sessionId":7,"status":"ACTIVE","blockingState":"ENFORCED","taskDescription":"Essay","plannedFocusMinutes":25,
                "activeFocusSeconds":60,"remainingFocusSeconds":1440,"startedAt":"2026-10-21T10:00:00Z","generatedAt":"2026-10-21T10:01:00Z",
                "dailyStreak":{"qualifyingSeconds":600,"targetSeconds":3600,"status":"IN_PROGRESS"}}"#,
        )
        .unwrap();
        assert_eq!(session.remaining_focus_seconds, 1440);
        assert_eq!(session.daily_streak, Some(DailyStreak { qualifying_seconds: 600, target_seconds: 3600 }));
        let target_only: CurrentSession = serde_json::from_str(
            r#"{"sessionId":null,"status":null,"taskDescription":null,"activeFocusSeconds":0,"remainingFocusSeconds":0,"dailyStreak":null}"#,
        )
        .unwrap();
        assert_eq!(target_only, CurrentSession::default());
    }

    #[test]
    fn nothing_is_kept_when_blocking_is_off() {
        let state: BlockingState = serde_json::from_str(
            r#"{"enforcementActive":false,"sessionId":null,"blockedApps":[],"blockRules":[{"host":"youtube.com","path":null}],"allowRules":[]}"#,
        )
        .unwrap();
        assert_eq!(rules_from(state), Rules::default());
    }

    #[test]
    fn the_token_is_forgotten_on_disconnect() {
        let path = std::env::temp_dir().join(format!("focusquest-token-{}", std::process::id()));
        save_private(&path, "fqd_secret").unwrap();
        let blocker = Blocker::load(path.clone());
        let idle = ChromeStatus::Idle;
        assert_eq!(blocker.state(), DesktopState { has_token: true, status: Status::NeverSynced, chrome: idle });
        assert_eq!(blocker.disconnect(), DesktopState { has_token: false, status: Status::NeverSynced, chrome: idle });
        assert!(!path.exists());
    }
}
