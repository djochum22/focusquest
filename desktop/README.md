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
- **Stays in the menu bar.** Closing the window keeps the app running; the menu bar icon (or the
  Dock icon) opens it again. **Quit FocusQuest** in that menu, or Cmd+Q, quits.
- **Says what is wrong** in the window if FocusQuest cannot start, or another program holds port 8080.

Not yet: keeping blocked Mac apps closed (step 4, part 3 in `docs/platform_roadmap.md`).

Website blocking is still the browser extension's: the app's window is a separate browser (macOS's
WebKit) with no extension and its own sign-in.

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
| `src-tauri/src/lib.rs` | The window, the menu bar icon, closing and quitting |
| `src-tauri/src/backend.rs` | Finds FocusQuest on 127.0.0.1:8080 (`/api/auth/setup-status`), or starts and stops the bundled copy; the sign-in secret kept in its data folder |
| `ui/index.html` | The page shown until FocusQuest answers, or why it could not start |
| `scripts/prepare-backend.sh` | Bundles the boot jar and a cut-down JRE |
| `src-tauri/icons/` | Made from `frontend/public/favicon.svg`; `tray.png` is the menu bar icon, black on transparent for macOS to tint |

Tests: `cargo test` in `src-tauri/` (also run by `scripts/test-all.sh`).
