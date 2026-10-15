# Handoff: what to do next (15 October 2026)

Start here. The details live in two longer notes:
- `docs/platform_roadmap.md`: the container, every browser, the desktop app
- `docs/camera_roadmap.md`: camera verification

## Where things stand

- **Desktop app (platform step 4): done on macOS.** `desktop/` is a Tauri app: a window on FocusQuest and a menu bar icon. It uses the Docker copy when that is running, otherwise starts its own, and quits blocked Mac apps while blocking is on. Tried live: Calculator was quit. Build it with `npm run build` in `desktop/`; see `desktop/README.md`.
- **Camera accuracy (C1d):** the `accuracy` command is built, away warns again, and getting up no longer counts as looking away. Phone detection is weak and left as it is for now. Looking away while coding still gives false alarms, which needs a fresh calibration before deciding the margin.
- **Every browser (platform step 3):** Chromium and Firefox done; Safari waits for Xcode.

## For you

1. **Calibrate the camera** (the companion is paired again, but the calibration is from 5 October): Settings, **Camera verification**, **Calibrate**.
2. **Label part A once more, for the full 3 minutes**, after calibrating: from `vision/`, `.venv/bin/focusquest-companion label --category CODING`, press `1` and work normally, `q` to stop. Then tell Claude, to settle the looking-away margin.
3. **Install Xcode** from the App Store, then `sudo xcode-select -s /Applications/Xcode.app`, and open Xcode once. Safari needs it.
4. **Try the extension's options page** (5 minutes): `docs/platform_roadmap.md`, "Waiting on the user", item 3.
5. **Decide about Firefox every day:** Mozilla signs the add-on, or Firefox Developer Edition. Same section, item 5.

**Remember:** quit the desktop app before starting Docker. If the app opened while Docker was down, it runs its own copy on port 8080 and keeps it; if `http://localhost:8080` then stops answering after Docker starts, run `orb restart docker`.

## For Claude, when you say go

1. **The looking-away margin** (C1d step 3), once part A is labelled again after calibrating.
2. **Safari**, once Xcode is installed: convert the extension with `xcrun safari-web-extension-converter` and ship it inside the desktop app.
