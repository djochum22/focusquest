# FocusQuest for Mac

FocusQuest as a Mac app: a window on FocusQuest and an icon in the menu bar. Built with
[Tauri v2](https://v2.tauri.app), so Windows can follow from the same project.

## What it does

- **Finds FocusQuest, or starts it.** If FocusQuest already answers at http://127.0.0.1:8080 (the
  Docker container, or `bootRun`), the app opens it and leaves it running when you quit. Otherwise it
  starts its own copy, bundled inside the app, with its data in
  `~/Library/Application Support/FocusQuest` and its log in `~/Library/Logs/FocusQuest/backend.log`,
  and stops it when you quit. Both copies use port 8080, where the browser extension and the camera
  companion look for FocusQuest, so only one can run at a time; they do not share data.
  The app decides when it opens: if you start Docker while it runs its own copy, the copy keeps
  the port. Quit the app first, then start Docker, then open the app.
- **Stays in the menu bar.** Closing the window keeps the app running; the menu bar icon (or the
  Dock icon) opens it again. **Quit FocusQuest** in that menu, or Cmd+Q, quits.
- **View, Reload (Cmd+R)** loads the web app again. The window loads it once, so after FocusQuest
  is updated (Docker rebuilt) it shows the old one until you reload or restart the app.
- **Says what is wrong** in the window if FocusQuest cannot start, or another program holds port 8080.

- **Quits blocked apps while blocking is on**: the apps on the Blocking page's **Apps** tab, at the
  same times websites are blocked (until the day's focus target, and during any session). It asks
  them to quit as Cmd+Q does, so they can still ask to save, and says why in a notification. Opened
  again, they are quit again within a second. It never quits itself, the Finder, the Dock or the
  login window.
- **Connects by itself.** Signed in to FocusQuest in its window, the web app gives it a token of
  its own (`fqd_`), which can only read what to block and the current session; Settings shows **Desktop app: Connected**,
  with Disconnect. The token is kept in `~/Library/Application Support/com.focusquest.desktop/desktop-token`.
  If FocusQuest cannot be reached, it keeps blocking what it last knew.
  Its window runs no browser extension, so there Settings leaves the browser extension out and the
  web app does not try to connect one.
- **Suggests your apps** on the Blocking page's **Apps** tab, as you type a name: the apps in
  /Applications, /System/Applications and ~/Applications (one folder down too), and those running.
  A picked app is saved with its bundle id, so it is matched exactly; one typed by name alone is
  matched by name, and gets its bundle id when edited in the app.

- **Closes blocked websites in Chrome**, by the same rules as the browser extension: a Chrome tab on
  a blocked site is sent to FocusQuest's blocked page within about a second, with a notification.
  The page shows the session's task and time left, and today's streak as a progress bar.
  The first time, macOS asks whether FocusQuest may control Google Chrome: click **OK**. Refused by
  mistake? System Settings, **Privacy & Security**, **Automation**, FocusQuest: turn on Google Chrome.
  Settings in the app's window says when it is not allowed. It looks at Chrome only while Chrome is
  open and something is blocked, so it never opens Chrome itself. Other browsers still need the
  browser extension. Quitting FocusQuest stops all blocking.
- **Opens at login**, from the first time it is opened. **Open at Login** in the menu bar menu
  switches that off. Keep the app in /Applications, and open it once from there after moving it.

The app's window is a separate browser (macOS's WebKit) with no extension and its own sign-in.

## Build

Needs Rust (https://rustup.rs), JDK 21 and Node.

```bash
cd desktop
npm install
npm run build        # bundles FocusQuest (scripts/prepare-backend.sh), then builds the app
open src-tauri/target/release/bundle/macos/FocusQuest.app
```

`scripts/prepare-backend.sh` builds the boot jar (backend and web app) and a JRE cut down with
`jlink` into `src-tauri/resources/backend/` (about 110 MB, not committed). Run it again after
changing the backend or the web app. The app is about 120 MB. It is not signed: built on this Mac it
opens as is; copied to another, right-click it and choose **Open** the first time.

For working on the app itself, `npm run dev` runs it without bundling (after `npm run backend` once,
if FocusQuest is not already running).

## Files

| File | What it does |
| --- | --- |
| `src-tauri/src/lib.rs` | The window, the menu bar icon (with Open at Login), closing and quitting; the one-second loop that quits blocked apps and closes blocked sites in Chrome |
| `src-tauri/src/blocking.rs` | The desktop token, reading the blocking state (apps and website rules) every 3 seconds, and which running apps to quit |
| `src-tauri/src/websites.rs` | Whether an address is blocked: the browser extension's matcher and rule precedence, in Rust |
| `src-tauri/src/chrome.rs` | Chrome's open tabs, and sending one to the blocked page, through Apple events (`osascript`) |
| `src-tauri/Info.plist` | Why FocusQuest asks to control Chrome (`NSAppleEventsUsageDescription`) |
| `src-tauri/src/apps.rs` | The running apps, and quitting one, through `NSWorkspace`; the installed apps, through `NSBundle` |
| `src-tauri/capabilities/web-app.json` | The four commands the web app may call, from FocusQuest's own address only |
| `src-tauri/src/backend.rs` | Finds FocusQuest on 127.0.0.1:8080 (`/api/auth/setup-status`), or starts and stops the bundled copy; the sign-in secret kept in its data folder |
| `ui/index.html` | The page shown until FocusQuest answers, or why it could not start |
| `scripts/prepare-backend.sh` | Bundles the boot jar and a cut-down JRE |
| `src-tauri/icons/` | Made from `frontend/public/favicon.svg`; `tray.png` is the menu bar icon, black on transparent for macOS to tint |

Tests: `cargo test` in `src-tauri/` (also run by `scripts/test-all.sh`); `cargo test -- --ignored` also reads this Mac's running apps and, with Chrome open, its tabs.
