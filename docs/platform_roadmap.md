# Platform work: where it stands

This note is for picking the work up again, for example in a new Claude session. It covers four things:
- running FocusQuest without VS Code
- running it in a container
- blocking in every browser
- a desktop app that also blocks Mac applications

The camera work has its own note, `docs/camera_roadmap.md`.

## Start here

**Where things stand (9 October 2026, end of day).**
- Steps 1 (one origin) and 2 (container) are committed, and so is step 3's first part, the bridge (`45aa0e0`).
- Step 3's second part, **Firefox, is committed**. The user checked it in Firefox 157, after the port fix ("Step 3 so far", part 2). The rename of "Chrome extension" to "Browser extension" in the web app is its own commit after it.
- All suites pass, end-to-end included. The container on :8080 is current.

**First thing next time: go on with step 3.**
1. **The options page for the backend address** (see "Later steps", step 3). Bear in mind that Firefox's match patterns cannot name a port, so a content script registered at run time for another address has the same limit; the bridge's own origin check covers it.
2. **Safari**, which goes into the step 4 Mac app.
3. **Edge and Brave** by hand (load `dist/` unpacked).
4. **A decision for the user, later:** a temporary Firefox add-on is gone after each restart. Using Firefox every day needs either Mozilla's signing (`npm run package:firefox` makes the zip; an unlisted add-on can be signed without being public) or Firefox Developer Edition with `xpinstall.signatures.required` set to `false`.
5. **Optional:** the backend comments and the docs still say "Chrome extension".

**Checking Firefox again** (about 10 minutes): `cd extension && npm run build:firefox`; in Firefox open `about:debugging#/runtime/this-firefox`, **Load Temporary Add-on…**, choose `extension/dist-firefox/manifest.json` (or **Reload** if loaded); open http://localhost:8080, sign in, and check Settings shows **Connected**, that a blocked site is blocked, and that the toolbar popup opens. On trouble, see the add-on's console (**Inspect** in `about:debugging`) and the tab's console.

**Remember:** the web app on :8080 is whatever was built when that copy started. After a web app or backend change, `docker compose up -d --build`, or restart `bootRun`. Only `npm run dev` (:5173) picks changes up live.

`.vscode/settings.json` stays out of every commit: VS Code's Java extension changed it on its own (`interactive` to `automatic`).

**Also waiting on the user: camera labelling** (C1d; see `docs/camera_roadmap.md`, "To do later, by the user: label"). It takes 20 to 30 minutes, and no camera code can go on until it is done. C1d step 2 (the `accuracy` command) comes after the desktop app, or whenever the labels are in.

## The two-week goal (from 8 October 2026)

1. **Run it on any computer** with one command and no VS Code. The user chose "each computer runs its own copy" over reaching one Mac from other devices.
2. **Block in every browser:** the Chromium browsers (Chrome, Edge, Brave, Arc, Opera), Firefox and Safari.
3. **A desktop app** that can also block Mac applications. Built with **Tauri v2**, which the user chose over Electron and native Swift because it keeps Windows, iOS and Android open.
4. **If time runs short, the desktop app slips.** The container and the browsers get finished properly, and the desktop app ends as a working proof of concept.

Later, not in these two weeks: Windows, iOS and Android apps.

## Status

| Step | What | State |
| --- | --- | --- |
| 1 | One app, one origin: the backend serves the built web app | Done |
| 2 | Container: Dockerfile and docker-compose | Done |
| 3 | Every browser: a bridge without `externally_connectable`, a Firefox build, Safari | **Under way:** bridge and Firefox done; options page and Safari open |
| 4 | Tauri desktop app, with Mac app blocking | Open |

## Done: step 1, one app, one origin

`./gradlew bootRun` in `backend/` is now the whole app at http://localhost:8080. The dev server (`npm run dev`, :5173) is only for working on the web app.

- **`backend/build.gradle.kts`**:
  - Tasks `installFrontend` (`npm ci`), `buildFrontend` (`npm run build`) and `webApp`, which syncs `frontend/dist` into `build/web-app/static`.
  - `bootJar` packages it into `BOOT-INF/classes/static`, and `bootRun` adds it to the classpath. Plain `test` runs do not build the web app.
  - `-PskipFrontendBuild` uses `frontend/dist` as it is. It is meant for the Docker image, which builds the web app in its own stage.
- **`WebAppConfig.java`** (new):
  - It serves `classpath:/static/`. A route with no file of its own gets `index.html`, so a reload on `/settings` works.
  - A missing file with a dot in its name (an old asset name, say) is a 404.
  - Paths under `/api`, `/h2-console`, `/actuator` and `/error` are never given `index.html`.
- **`SecurityConfig.java`**:
  - `/api/**` needs `ROLE_USER`.
  - GETs that `WebAppConfig.isWebAppRequest` accepts are open, because they are the web app's files and hold no data.
  - Everything else still needs a token. The existing security tests, which expect 401 for `/actuator/env` and `/h2-console/`, still pass.
- **`application.yml`**: four new environment variables. Nothing changes when they are unset.
  - `FOCUSQUEST_PORT` (default 8080)
  - `FOCUSQUEST_BIND` (default 127.0.0.1)
  - `FOCUSQUEST_DATA_DIR` (default `./data`)
  - The CORS comment now says 5173 is only for the dev server.
- **`frontend/src/api/client.ts`**: a build calls its own origin. `npm run dev` calls `http://127.0.0.1:8080`, and `VITE_API_BASE_URL` still overrides both.
- **The extension**:
  - `WEB_APP_ORIGINS` in `src/utils/config.ts` holds `localhost:8080`, `127.0.0.1:8080` and `localhost:5173`. A build given `FOCUSQUEST_FRONTEND_URL` (the e2e suite) trusts only that one.
  - `externally_connectable` in `manifest.json` has the same three origins.
  - `handleExternalMessage` takes `allowedOrigins`, a list.
  - `FRONTEND_URL`, the blocked page's link, now defaults to `http://localhost:8080`.
- **Docs:** `README.md` (one command, a table of the variables, troubleshooting), `frontend/README.md`, `frontend/.env.example`, `extension/README.md`.

## Done: step 2, container

- **`Dockerfile`**, multi-stage:
  1. A Node stage runs `npm ci && npm run build` in `frontend/`.
  2. A JDK 21 stage copies `dist` in and runs `./gradlew bootJar -PskipFrontendBuild`.
  3. An Eclipse Temurin 21 JRE runtime runs the jar as a non-root user, with `FOCUSQUEST_BIND=0.0.0.0` and `FOCUSQUEST_DATA_DIR=/app/data`.
- **`docker-compose.yml`**:
  - a named volume on `/app/data`
  - the port as `127.0.0.1:8080:8080`, so it is only reachable from this computer and the companion's localhost-only sandbox keeps working
  - `FOCUSQUEST_JWT_SECRET` from `.env`, with a `.env.example` committed and `.env` ignored
- Add a `.dockerignore` covering `node_modules`, `build`, `dist`, `data`, `.gradle` and `vision/.venv`.
- **README:** a "Run with Docker" section, and how to back up the volume. Export in Settings also works as a backup.
- **The companion stays outside the container.** Docker on macOS cannot reach the camera (`camera_roadmap.md`, "Decisions").
- **Verify:**
  - `docker compose up` on a clean build, then create the account and run a session.
  - `docker compose restart`, then check the data and the sign-in survive.
  - The extension, pointed at it, blocks sites.
  - The companion still pairs.

## Step 3 so far: every browser

**Part 1, the bridge (committed, `45aa0e0`).** The user checked it in Chrome: connected on reload, blocking, and "not installed" at once with the extension off.
- `extension/src/content/webAppBridge.ts` (the relay, tested in `tests/webAppBridge.test.ts`) and `webAppBridgeScript.ts` (the content script entry, built as a plain script by `scripts/build.mjs`).
- The manifest: `content_scripts` on the three web app origins at `document_start`, in place of `externally_connectable`, and the `scripting` permission.
- The service worker takes the bridge's messages through `runtime.onMessage`, checks the page's origin as before (`handleExternalMessage`), and on install adds the bridge to web app tabs already open.
- `frontend/src/utils/extensionBridge.ts` posts to the page and waits for the matching reply. With no `data-focusquest-extension` mark it reports "not installed" at once. `VITE_EXTENSION_ID` is gone. The manifest `key` stays: it keeps the id stable, and the backend's CORS list still names it.
- Docs: `extension/README.md`, `docs/technical_architecture.md`.
- All suites pass, end-to-end included.

**Part 2, Firefox (committed; the user checked it in Firefox 157 on 9 October 2026).**
- No API namespace work was needed: in Manifest V3, Firefox's `chrome.*` returns promises too, and its alarm minimum is Chrome's 30 seconds.
- `npm run build:firefox` writes `dist-firefox/`; `toFirefox` in `scripts/build.mjs` makes the manifest: `background.scripts` (module) for the service worker, no `key` or `minimum_chrome_version`, `browser_specific_settings.gecko` with id `focusquest@local`, minimum 140, `data_collection_permissions: none`.
- **Found in the user's first try (9 October 2026): not connected.** Firefox's match patterns cannot name a port; one that does installs without complaint and never matches, so the bridge never ran. `toFirefox` now drops the ports (`http://localhost/*`, `http://127.0.0.1/*`), and `startBridge` takes `allowedOrigins` (`WEB_APP_ORIGINS`) and does nothing elsewhere, so another local site cannot learn the extension is there. Checked in a headless Firefox 157 over WebDriver BiDi: the bridge answers on :8080 and stays silent on another port.
- `web-ext` is a dev dependency: `npm run lint:firefox` (0 errors; one warning about Firefox for Android, not a target) and `npm run package:firefox`.
- The backend refused `moz-extension://` and `safari-web-extension://` origins (403). `CorsConfig` now admits them by pattern on `/api/extension/**` only; tested in `SecurityConfigurationIntegrationTest`.
- Docs: both READMEs (loading in Firefox, signing, private windows).

**Decided 9 October 2026:** the extension's backend address in an options page moved here from step 2. Before the bridge, `externally_connectable` fixed the web app's origins in the manifest, so an extension pointed elsewhere could not be connected; and with each computer running its own copy on 127.0.0.1:8080, nothing needs it yet.

## Later steps (planned)

**Step 3: every browser (days 5–8).** The bridge, the API namespace and the Firefox manifest are done (see "Step 3 so far"); the options page, testing and Safari are open.
- **The bridge.** Firefox and Safari have no `externally_connectable`. Replace it with a content script injected on the web app's origin, talking to the page through `window.postMessage`. Code to change:
  - `frontend/src/utils/extensionBridge.ts`
  - `onMessageExternal` in `extension/src/background/serviceWorker.ts`

  This also drops the dependency on the pinned extension id.
- **The backend address in an options page** (moved from step 2), instead of only at build time (`extension/src/utils/config.ts`, `BACKEND_BASE_URL`), so one built extension works against any port or machine. The bridge's content script then has to be registered for that origin at run time (`chrome.scripting.registerContentScripts`).
- **API namespace.** Use `browser ?? chrome`, or `webextension-polyfill`.
- **Manifests.** The build gives one manifest per browser. The Firefox one needs:
  - `background.scripts`
  - `browser_specific_settings.gecko.id`
  - a minimum version of 128 or later
  - packaging with `web-ext`
- **Testing.** Try Edge and Brave by hand. The e2e suite stays on Chromium, and Firefox gets a manual checklist.
- **Safari.** Convert the build with `xcrun safari-web-extension-converter`. It must ship inside a Mac app, so it goes into the step 4 bundle.

**Step 4: Tauri desktop app (days 9–14).**
- **Location:** `desktop/`.
- **Shell:**
  - It starts the bundled backend (a `jlink`ed JRE plus the boot jar) as a sidecar, with data in `~/Library/Application Support/FocusQuest`.
  - It waits for the backend, opens a window on it, and adds a menu bar icon. Quitting the app stops the backend.
- **Blocked apps in the backend:**
  - migration V18, a blocked-apps list of bundle ids and names
  - an endpoint like `/api/extension/blocking-state`, with the same rule: blocked until the daily target, and during any session
  - a Settings row, following the UI preferences: a collapsible row, an InfoTip, no cards
- **Enforcement:**
  - Watch `NSWorkspace` launch and activation notifications, and hide or quit a blocked app. This uses Rust `objc2`, or a small Swift plugin.
  - No special entitlement is needed outside the App Store.
- **Docs:** update `docs/requirements_specification.md:105`, which lists desktop-app blocking as out of scope.
- **Fallback:** the shell plus app blocking for a hard-coded list.

**Later, not these two weeks:**
- **Windows.** Tauri builds it from the same project, and app blocking needs a Win32 process watcher.
- **iOS and Android need a decision first.** The Java backend cannot run on a phone, so they need either a reachable server or a sync model, and that changes the local-first, single-user design.
- **App blocking on phones:**
  - iOS: only through Apple's Screen Time API (FamilyControls), which needs an entitlement Apple must approve.
  - Android: UsageStats plus an Accessibility service.

## Working agreement

- The user reviews each step before it is committed, and each step is its own commit.
- Run `scripts/test-all.sh` before and after each step, plus `cd e2e && npx playwright test` when the extension or the web app's wiring changes.
- The full plan from 8 October 2026 is also in `~/.claude/plans/i-would-like-to-hidden-sparrow.md`.
