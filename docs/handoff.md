# Handoff: what to do next (15 October 2026)

Start here. The details live in two longer notes:
- `docs/platform_roadmap.md`: the container, every browser, the desktop app
- `docs/camera_roadmap.md`: camera verification

## Where things stand

- **Desktop app (platform step 4): done on macOS.** `desktop/` is a Tauri app: a window on FocusQuest and a menu bar icon. It uses the Docker copy when that is running, otherwise starts its own, and quits blocked Mac apps while blocking is on. Tried live: Calculator was quit. Build it with `npm run build` in `desktop/`; see `desktop/README.md`.
- **Camera accuracy (C1d): done.** The `accuracy` command is built, away warns again, getting up no longer counts as looking away, and after calibrating again the looking-away margin stays at 5° (2 false seconds in 4½ minutes, no false warnings). Phone detection is weak and left as it is.
- **Every browser (platform step 3):** Chromium and Firefox done; Safari built as its own small app, "FocusQuest for Safari", not yet tried in Safari.

Nothing is waiting right now. Everything below is for later.

## Later, for you

### Try Safari (about 10 minutes)

Only when you want to use Safari. FocusQuest for Safari is a small app that carries the extension: Safari takes extensions only inside an app.

1. **Add your Apple ID to Xcode**: open Xcode, then **Settings**, **Accounts**, **+**, **Apple ID**, and sign in. It is free and publishes nothing. Without it, Safari forgets the extension each time it restarts.
2. **Build the app**, in a terminal:

   ```bash
   cd ~/Desktop/Code/focusquest/extension
   npm run build:safari
   ```

   It should say **Signing with team …**. If it says **No Apple ID in Xcode**, step 1 did not take.
3. **Open the app once**: `extension/safari-build/FocusQuest for Safari.app` (in Finder, or `open "safari-build/FocusQuest for Safari.app"` from the same terminal).
4. **Turn the extension on**: in Safari, **Settings**, **Extensions**, tick **FocusQuest**, and allow it on all websites (it needs that to block sites).
5. **Check it connects**: in Safari, open http://localhost:8080 and sign in. **Settings**, **Browser extension** should say **Connected** within a few seconds.
6. **Check it blocks**: visit one of your blocked sites. It should show FocusQuest's "This site is blocked" page.

Then tell Claude what happened at each step: something like "Read docs/handoff.md. I tried Safari: steps 1 to 4 worked, step 5 said …". If a step fails, a screenshot or the exact message helps.

### Optional: the extension's options page (5 minutes)

Only needed if FocusQuest ever runs somewhere other than port 8080. Steps in `docs/platform_roadmap.md`, "Waiting on the user", item 2.

### Decided, nothing to do

- Firefox every day: not needed (15 October 2026, the user does not use Firefox regularly).
- Phone detection: left as it is (15 October 2026).

**Remember:** quit the desktop app before starting Docker. If the app opened while Docker was down, it runs its own copy on port 8080 and keeps it; if `http://localhost:8080` then stops answering after Docker starts, run `orb restart docker`. After FocusQuest is updated, press Cmd+R in the desktop app's window to load the new version.

## Later, for Claude

1. **Safari**: fix whatever the user's first try turns up. Most likely to need work: whether Safari's `declarativeNetRequest` redirects to the blocked page, and whether the bridge connects (`docs/platform_roadmap.md`, "Safari").
