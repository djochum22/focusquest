//! Chrome's open tabs, and sending one to FocusQuest's blocked page, through Apple events
//! (`osascript`, in JavaScript). macOS asks the user once whether FocusQuest may control Chrome
//! (`NSAppleEventsUsageDescription` in Info.plist); refused, every call fails with error -1743.
//!
//! Only called while Chrome runs: an Apple event to an app that is not running would open it.
//! Tabs are addressed by their ids, not their places, so a window brought to the front between
//! reading and redirecting cannot send the wrong tab away.

use std::process::Command;

use serde::{Deserialize, Serialize};

pub const BUNDLE_ID: &str = "com.google.Chrome";

/// How the last look at Chrome went, for Settings in the web app.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize)]
#[serde(rename_all = "kebab-case")]
pub enum ChromeStatus {
    /// Not looked at: Chrome is not running, or nothing is blocked.
    Idle,
    Ok,
    /// The user has not allowed FocusQuest to control Chrome.
    Denied,
    Error,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Tab {
    pub window_id: String,
    pub tab_id: String,
    pub url: String,
}

/// One Apple event per property, each returning every window's tabs at once.
const READ: &str = "function run() {
  const chrome = Application('Google Chrome')
  return JSON.stringify({ windows: chrome.windows.id(), tabs: chrome.windows.tabs.id(), urls: chrome.windows.tabs.url() })
}";

/// Arguments in threes: window id, tab id, the address to load.
const REDIRECT: &str = "function run(argv) {
  const chrome = Application('Google Chrome')
  for (let i = 0; i + 2 < argv.length; i += 3) {
    try {
      chrome.windows.byId(argv[i]).tabs.byId(argv[i + 1]).url = argv[i + 2]
    } catch (e) {}
  }
}";

/// Chrome gives ids as text, older versions as numbers.
#[derive(Deserialize)]
#[serde(untagged)]
enum Id {
    Text(String),
    Number(i64),
}

impl Id {
    fn text(&self) -> String {
        match self {
            Id::Text(text) => text.clone(),
            Id::Number(number) => number.to_string(),
        }
    }
}

#[derive(Deserialize)]
struct Read {
    windows: Vec<Id>,
    tabs: Vec<Vec<Id>>,
    urls: Vec<Vec<Option<String>>>,
}

/// Every open tab, in every window (incognito ones too).
pub fn tabs() -> Result<Vec<Tab>, ChromeStatus> {
    let output = run(READ, &[])?;
    let read: Read = serde_json::from_str(output.trim()).map_err(|_| ChromeStatus::Error)?;
    Ok(tabs_from(read))
}

fn tabs_from(read: Read) -> Vec<Tab> {
    let mut tabs = Vec::new();
    for ((window_id, tab_ids), urls) in read.windows.iter().zip(&read.tabs).zip(&read.urls) {
        for (tab_id, url) in tab_ids.iter().zip(urls) {
            if let Some(url) = url {
                tabs.push(Tab { window_id: window_id.text(), tab_id: tab_id.text(), url: url.clone() });
            }
        }
    }
    tabs
}

/// Loads each address in its tab.
pub fn redirect(to: &[(&Tab, String)]) -> Result<(), ChromeStatus> {
    if to.is_empty() {
        return Ok(());
    }
    let args: Vec<String> = to.iter().flat_map(|(tab, url)| [tab.window_id.clone(), tab.tab_id.clone(), url.clone()]).collect();
    run(REDIRECT, &args).map(|_| ())
}

fn run(script: &str, args: &[String]) -> Result<String, ChromeStatus> {
    let output = Command::new("/usr/bin/osascript")
        .args(["-l", "JavaScript", "-e", script])
        .args(args)
        .output()
        .map_err(|_| ChromeStatus::Error)?;
    if output.status.success() {
        return Ok(String::from_utf8_lossy(&output.stdout).into_owned());
    }
    let error = String::from_utf8_lossy(&output.stderr);
    // -1743: not allowed to send Apple events; -1744: would have to ask, and could not.
    if error.contains("-1743") || error.contains("-1744") {
        Err(ChromeStatus::Denied)
    } else {
        eprintln!("Cannot look at Chrome's tabs: {}", error.trim());
        Err(ChromeStatus::Error)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn tabs_are_read_with_their_window_and_tab_ids() {
        let read: Read = serde_json::from_str(
            r#"{"windows":["10",20],"tabs":[["1","2"],["3"]],"urls":[["https://youtube.com/",null],["chrome://newtab/"]]}"#,
        )
        .unwrap();
        assert_eq!(
            tabs_from(read),
            vec![
                Tab { window_id: "10".into(), tab_id: "1".into(), url: "https://youtube.com/".into() },
                Tab { window_id: "20".into(), tab_id: "3".into(), url: "chrome://newtab/".into() },
            ]
        );
    }

    // Asks for, then uses, the right to control Chrome, so it is left out of the normal run:
    // `cargo test -- --ignored` with Chrome open.
    #[test]
    #[ignore]
    fn reads_chromes_tabs() {
        let tabs = tabs().expect("Chrome running, and allowed");
        assert!(!tabs.is_empty());
    }
}
