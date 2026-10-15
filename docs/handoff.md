# Handoff: what to do next (15 October 2026)

Start here. The details live in two longer notes:
- `docs/platform_roadmap.md`: the container, every browser, the desktop app
- `docs/camera_roadmap.md`: camera verification

## Where things stand

- **Desktop app (platform step 4): done on macOS.** `desktop/` is a Tauri app: a window on FocusQuest and a menu bar icon. It uses the Docker copy when that is running, otherwise starts its own, and quits blocked Mac apps while blocking is on. Tried live: Calculator was quit. Build it with `npm run build` in `desktop/`; see `desktop/README.md`.
- **Camera accuracy (C1d): done.** The `accuracy` command is built, away warns again, getting up no longer counts as looking away, and after calibrating again the looking-away margin stays at 5° (2 false seconds in 4½ minutes, no false warnings). Phone detection is weak and left as it is.
- **Every browser (platform step 3):** Chromium and Firefox done; Safari built as its own small app, "FocusQuest for Safari", not yet tried in Safari.

## For you

1. **Try Safari** (only when you want it): add your Apple ID in Xcode, **Settings**, **Accounts**; then in `extension/`, `npm run build:safari`; open `extension/safari-build/FocusQuest for Safari.app` once; in Safari, **Settings**, **Extensions**, turn FocusQuest on and allow it on all websites. Tell Claude what happens.
2. **Optional: try the extension's options page** (5 minutes): `docs/platform_roadmap.md`, "Waiting on the user", item 2. Only needed if FocusQuest ever runs somewhere other than port 8080.

Firefox every day: not needed, decided 15 October 2026 (the user does not use Firefox regularly).

**Remember:** quit the desktop app before starting Docker. If the app opened while Docker was down, it runs its own copy on port 8080 and keeps it; if `http://localhost:8080` then stops answering after Docker starts, run `orb restart docker`.

## For Claude, when you say go

1. **Safari**: fix whatever your first try turns up.
