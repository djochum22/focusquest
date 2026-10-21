# Handoff: what to do next (21 October 2026)

Start here. The details live in two longer notes:
- `docs/platform_roadmap.md`: the container, every browser, the desktop app
- `docs/camera_roadmap.md`: camera verification

## Where things stand

Everything planned is done. Nothing is waiting, for the user or for Claude; the lists below are optional.

- **Desktop app (platform step 4): done on macOS.** `desktop/` is a Tauri app: a window on FocusQuest and a menu bar icon. It uses the Docker copy when that is running, otherwise starts its own, and quits blocked Mac apps while blocking is on. Tried live: Calculator was quit. Build it with `npm run build` in `desktop/`; see `desktop/README.md`.
- **Camera accuracy (C1d): done.** The `accuracy` command is built, away warns again, getting up no longer counts as looking away, and after calibrating again the looking-away margin stays at 5° (2 false seconds in 4½ minutes, no false warnings). Phone detection is weak and left as it is.
- **Every browser (platform step 3): done.** Chromium and Firefox; Safari, as its own small app "FocusQuest for Safari", connects and blocks (tried by the user 21 October 2026). Connecting needed two fixes: the bridge's match patterns, and a browser event Safari lacks (`docs/platform_roadmap.md`, "Safari").

The desktop app's window is not Safari and runs no browser extension, so its Settings never shows the browser extension as connected; that is expected.

## Safari after a change

After the extension's code changes, in a terminal:

```bash
cd ~/Desktop/Code/focusquest/extension
npm run build:safari
open "safari-build/FocusQuest for Safari.app"
```

It should say **Signing with team …** (team P3NCC9658P, the Apple ID in Xcode). If it says **No Apple ID team found**: Xcode, **Settings**, **Accounts**, select the Apple ID, **Manage Certificates…**, **+**, **Apple Development**. Then check in Safari's **Settings**, **Extensions** that **FocusQuest** is still ticked and allowed on all websites, and reload http://localhost:8080.

## Optional, for you

- **The extension's options page** (5 minutes): only needed if FocusQuest ever runs somewhere other than port 8080. Steps in `docs/platform_roadmap.md`, "Waiting on the user", item 2.
- **Camera labelling in different light**, if you want the accuracy numbers to cover daylight and lamps (`docs/camera_roadmap.md`, "Start here").

### Decided, nothing to do

- Firefox every day: not needed (15 October 2026, the user does not use Firefox regularly).
- Phone detection: left as it is (15 October 2026).

**Remember:** quit the desktop app before starting Docker. If the app opened while Docker was down, it runs its own copy on port 8080 and keeps it; if `http://localhost:8080` then stops answering after Docker starts, run `orb restart docker`. After FocusQuest is updated, press Cmd+R in the desktop app's window to load the new version.

## Optional, for Claude (only after the user says go)

1. **Edge and Brave** by hand: load `extension/dist/` unpacked.
2. **Wording**: the backend comments and some docs still say "Chrome extension" where any browser is meant.
3. **Signing and notarising the desktop app**, so it opens on another Mac without a warning (needs a paid Apple Developer account).
4. From the requirements: a `BlockingOverride` record with a reason (section on overrides), and `GET /api/focus-sessions/{id}`.
5. A better way of seeing a phone in hand (camera finding 3).

**Looking inside Safari from the terminal** (how its connection was fixed): the user ticks **Allow JavaScript from Apple Events** (Safari **Settings**, **Developer**); then `osascript -e 'tell application "Safari" to do JavaScript "…" in (first tab of first window whose URL contains "localhost:8080")'`. For code that needs the extension's own rights, the user opens the extension's options page (Safari **Settings**, **Extensions**, **FocusQuest**, **Settings**) and the same command runs there.
