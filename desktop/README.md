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
opens as is; for another Mac, see [Giving it to someone else](#giving-it-to-someone-else).

If `npm run build` stops at "failed to run cargo metadata", that terminal cannot find Rust: run
`source "$HOME/.cargo/env"` (or open a new terminal) and build again.

### Updating the installed app

After a build, quit FocusQuest and replace the copy in /Applications. This works from any folder,
and deletes the old app only once the new build is there. `ditto` merges into an existing app and
never deletes, so the old one is removed first rather than copied over. Data in
`~/Library/Application Support/FocusQuest` is not touched.

```bash
NEW=~/Desktop/Code/focusquest/desktop/src-tauri/target/release/bundle/macos/FocusQuest.app
[ -d "$NEW" ] && rm -rf /Applications/FocusQuest.app && ditto "$NEW" /Applications/FocusQuest.app && open /Applications/FocusQuest.app
```

Open it from /Applications as above, so Open at Login keeps working. macOS may ask again whether
FocusQuest may control Google Chrome.

### Giving it to someone else

The app carries everything it runs on, so the other Mac needs no Java, Rust, Node or Docker. It
does need Apple Silicon (M1 or newer; the build is arm64 only) and macOS 12 or newer. Zip it with
`ditto`, not the Finder's Compress, so the bundled JRE stays intact (about 90 MB):

```bash
ditto -c -k --keepParent src-tauri/target/release/bundle/macos/FocusQuest.app ~/Desktop/FocusQuest.zip
```

They unzip it and move FocusQuest.app to /Applications. As it is not signed, macOS refuses to open
it the first time (right-click, **Open** no longer gets past this since macOS 15): open it once,
then in System Settings, **Privacy & Security**, click **Open Anyway**. Or, in Terminal:

```bash
xattr -dr com.apple.quarantine /Applications/FocusQuest.app
```

It starts with empty data and its own account, and asks to control Google Chrome as above. Each
new version is sent and let through the same way.

For working on the app itself, `npm run dev` runs it without bundling (after `npm run backend` once,
if FocusQuest is not already running).

## Repair

Easiest first. None of these touch the data in `~/Library/Application Support/FocusQuest`, except
the last.

1. **Reload or restart.** Cmd+R in the window loads the web app again. Otherwise quit (Cmd+Q, or
   **Quit FocusQuest** in the menu bar menu) and open it again from /Applications.
2. **It does not start.** The window says why. For more, the bundled copy's log, and what holds
   port 8080:

   ```bash
   tail -50 ~/Library/Logs/FocusQuest/backend.log
   lsof -i :8080
   ```

   Docker or `bootRun` started after the app is the usual cause: quit the app, start Docker, then
   open the app.
3. **Apps or sites are not blocked.** Settings in the app's window should say **Desktop app:
   Connected**. If not, Disconnect, and sign in again in that window; it connects by itself. To
   force a new token, quit the app, delete it, and open the app again:

   ```bash
   rm ~/Library/Application\ Support/com.focusquest.desktop/desktop-token
   ```

4. **Chrome tabs are not closed.** System Settings, **Privacy & Security**, **Automation**,
   FocusQuest: turn on Google Chrome. If FocusQuest is not listed, reset the permission, and macOS
   asks again the next time something is blocked:

   ```bash
   tccutil reset AppleEvents com.focusquest.desktop
   ```

5. **Reinstall**, for a damaged app or Open at Login not working: build (see [Build](#build)), then
   replace the installed app as in [Updating the installed app](#updating-the-installed-app), and
   open it from /Applications.
6. **Start from empty data**, as a last resort: quit the app and rename
   `~/Library/Application Support/FocusQuest` (to `FocusQuest.bak`, say) rather than deleting it.
   This loses the bundled copy's sessions and settings; the Docker copy's data is separate.

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

## Pair Companion
If you meant the camera companion (vision/), the fixes are:

Pair again: in Settings, click Pair the companion program, then run focusquest-companion pair. The new code replaces the old one.
Calibrate again after moving the camera or switching to another one.
Reinstall: in vision/, run rm -rf .venv && python3 -m venv .venv && .venv/bin/pip install -e '.[camera]'
