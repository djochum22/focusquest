# Platform work: where it stands

This note is for picking the work up again, for example in a new Claude session. It covers four things:
- running FocusQuest without VS Code
- running it in a container
- blocking in every browser
- a desktop app that also blocks Mac applications

The camera work has its own note, `docs/camera_roadmap.md`.

## Start here

**Read `docs/handoff.md` first** (21 October 2026): the short list of what to do next, for the user and for Claude.

**Where things stand (21 October 2026).** All four steps are done on macOS; Safari connects and blocks since 21 October 2026. Everything is committed.

| Commit | What |
| --- | --- |
| `45aa0e0`, `fd19bb7`, `7522901`, `95b33d7` | Step 3: the bridge, the Firefox build, "browser extension" wording, the options page |
| `f81fba3`, `8204609`, `0203de9` | Step 3: the Safari app, signing with the Apple ID's team, connecting in Safari |
| `ccf0807` | Step 4, part 2: the desktop app shell in `desktop/` |
| `413abbb` | Step 4, part 1: blocked apps in the backend and on the Blocking page |
| `47aca31` | Step 4, part 3: the desktop app quits blocked apps (desktop token, V19); tried live, Calculator was quit |
| `5a3c646` | Step 4, part 4: picking apps by bundle id in the desktop app |
| (21 October 2026) | Step 4, part 5: the desktop app closes blocked websites in Chrome, and opens at login |

The container on :8080 is current (rebuilt 15 October 2026), with migrations V18 and V19 applied to the real volume. The desktop app is built at `desktop/src-tauri/target/release/bundle/macos/FocusQuest.app` and connected to it.

**Tools on this Mac (15 October 2026):** Rust 1.99 with rustup (`~/.cargo/bin`; a new shell picks it up). Xcode 27 is installed and selected (`xcode-select -p` gives `/Applications/Xcode.app/Contents/Developer`). Apple ID and its "Apple Development" certificate added in Xcode 21 October 2026 (team P3NCC9658P; Xcode 27 saves no team until the certificate exists).

**Waiting on the user:**

1. ~~Try Safari~~: done 21 October 2026, connects and blocks after two fixes (see "Safari" under step 3).
2. **Try the options page** (5 minutes; it is committed, but the user has not tried it yet). Reload the extension: **Reload** in `about:debugging` for Firefox, or in `chrome://extensions` for Chrome. Then:
   1. Click the toolbar icon, then **Change the FocusQuest address**.
   2. Try a bad address (it should be refused), then `127.0.0.1:8080`, then **Use this computer**.
   3. Open http://localhost:8080 and check Settings shows **Browser extension: Connected** again. The extension is signed out on each change and reconnects by itself.

   Not yet checked: a real sync through another address while connected. The unit tests cover where the calls go, and the browser checks ran without an account.
**Decided 15 October 2026: no Firefox for every day.** The user does not use Firefox regularly, so the add-on stays a temporary one, loaded by hand for checks. If that changes: Mozilla signs it (`npm run package:firefox` makes the zip; an unlisted add-on can be signed without being public), or Firefox Developer Edition with `xpinstall.signatures.required` set to `false`.

**Next for Claude (only after the user says go):**

1. ~~Safari~~: done 21 October 2026.
2. **Edge and Brave** by hand: load `extension/dist/` unpacked.
3. Optional: the backend comments and the docs still say "Chrome extension" in places.
4. Later: signing and notarising the desktop app, so it opens on another Mac without a warning.

**Checking Firefox again** (about 10 minutes): `cd extension && npm run build:firefox`; in Firefox open `about:debugging#/runtime/this-firefox`, **Load Temporary Add-on…**, choose `extension/dist-firefox/manifest.json` (or **Reload** if loaded); open http://localhost:8080, sign in, and check Settings shows **Connected**, that a blocked site is blocked, and that the toolbar popup opens. On trouble, see the add-on's console (**Inspect** in `about:debugging`) and the tab's console.

**Testing in a real browser without the user** (how the Firefox port bug and the options page were checked): Firefox runs headless with `--remote-debugging-port` (add `--remote-allow-system-access` to reach extension pages) and takes `webExtension.install` over WebDriver BiDi; Chromium through Playwright's `launchPersistentContext` with `--load-extension`, as in `e2e/support/fixtures.ts`. A small Node proxy on another port gives the web app a second address.

**Remember:**
- The web app on :8080 is whatever was built when that copy started. After a web app or backend change, `docker compose up -d --build` (if `docker info` fails, `orb start` first), or restart `bootRun`. Only `npm run dev` (:5173) picks changes up live.
- Firefox's match patterns cannot name a port: one that does installs without complaint and never matches.
- **Quit the desktop app before starting Docker.** Opened while nothing answers on :8080, the app starts its own copy there and keeps it; Docker's container then starts but cannot be reached, and OrbStack's forwarding of 8080 stayed broken until `orb restart docker` (15 October 2026).
- After changing the backend or the web app, the desktop app's own copy needs `npm run build` in `desktop/` (it bundles `prepare-backend.sh`'s jar); its window on Docker needs only the container rebuilt.
- `.vscode/settings.json` stays out of every commit: VS Code's Java extension changed it on its own (`interactive` to `automatic`).

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
| 3 | Every browser: a bridge without `externally_connectable`, a Firefox build, Safari | **Done:** bridge, Firefox, options page; Safari built 15 October 2026, connects and blocks since 21 October |
| 4 | Tauri desktop app, with Mac app blocking | **Done** on macOS: blocked apps, the shell, enforcement, picking apps by bundle id, blocked websites in Chrome, opening at login. Open: signing |

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

**Part 3, the options page (`95b33d7`; not yet tried by the user).**
- One setting, "FocusQuest address", because the backend serves its API and the web app from one origin. `src/utils/address.ts` parses it (`localhost:9000` gives `http://localhost:9000`), stores it in `chrome.storage.local` as `webAppUrl`, and gives the API base, the web app's link and the trusted origins; unset, `config.ts`'s defaults apply.
- Saving a new address removes the token, so it is never sent to another host; the web app connects again by itself. Failed syncs keep the last snapshot, so blocking does not end.
- `src/background/bridgeRegistration.ts` registers the bridge for the address with `chrome.scripting.registerContentScripts`, unless a manifest pattern already covers it (in Firefox, any port of localhost). The bridge takes `loadConfiguredOrigin` and marks the page a moment later on that address.
- The backend client reads the address for each request; the popup links there and has a quiet **Change the FocusQuest address** link; the blocked page links there.
- Tests: `tests/address.test.ts`, one more in `backendClient.test.ts` and `webAppBridge.test.ts`. Checked by hand in headless Firefox (WebDriver BiDi, with `--remote-allow-system-access` to reach the options page) and in Playwright's Chromium, against a proxy on :8090: the bridge answers there after saving, still on :8080, and not on an unrelated page; "Use this computer" removes the registration.

**Decided 9 October 2026:** the extension's backend address in an options page moved here from step 2. Before the bridge, `externally_connectable` fixed the web app's origins in the manifest, so an extension pointed elsewhere could not be connected; and with each computer running its own copy on 127.0.0.1:8080, nothing needs it yet.

**Safari (built 15 October 2026; connects and blocks in Safari 26.6 since 21 October).** Built with Xcode 27.
- **Decided by the user:** its own small app, "FocusQuest for Safari", launched only to use Safari (the user rarely does), rather than inside the desktop app as first planned; signed with a free Apple ID in Xcode, so Safari keeps it on (the script falls back to signing for this Mac only, which needs Develop, "Allow Unsigned Extensions" at each Safari start). Firefox for every day is not needed either.
- `node scripts/build.mjs --safari` builds `dist-safari/` (`toSafari`: no `background.type`, no `key` or `minimum_chrome_version`, no `open_in_tab`; the bridge in two entries, with its ports and port-less as in Firefox; esbuild target `safari17`). `scripts/build-safari.sh` runs `safari-web-extension-converter` into `safari-build/` each time, sets the app's id to `com.focusquest.safari` (the converter names it after the app, and an embedded extension's id must start with its app's), finds a team from `FOCUSQUEST_TEAM`, Xcode's preferences or (added 21 October 2026, as Xcode 27 saves no team there before the certificate exists) the keychain's "Apple Development" certificate, and builds with `xcodebuild`. `npm run build:safari` does both.
- The manifest now has icons (`extension/icons/`, made from the web app's favicon), which the converter wanted and every browser shows.
- **Found in the user's first try (21 October 2026): not connected**, the page saying to install the extension. Checked from the terminal with Safari's "Allow JavaScript from Apple Events" (`osascript` … `do JavaScript`, in the web app's tab and in the extension's options page, which runs with the extension's rights):
  - *The bridge did not run on `localhost:8080`* with the port-less patterns alone. `toSafari` now keeps the patterns with their ports and adds the port-less ones as a second entry; the bridge then ran (which of the two Safari honoured was not isolated). When both match, a flag in the content scripts' world starts the bridge once (`__FOCUSQUEST_SAFARI__`, Safari's build only: elsewhere a reloaded extension's copy, added to open tabs, must take over from the old one).
  - *The worker never started:* Safari has no `webNavigation.onHistoryStateUpdated` (nor `declarativeNetRequest.ResourceType` and the other enums, used only as types here), so `registerNavigationGuard` threw and the page got an empty reply. Found by importing `background/serviceWorker.js` in the options page. The guard now registers it only where it exists (`tests/navigationGuard.test.ts`); in Safari, the open-tab guard's `tabs.onUpdated` catches single-page-app navigations.
  - Then Settings showed **Browser extension: Connected**, and the bridge answered `{ ok: true, hasToken: true, status: "ok" }`.
- The desktop app's window is a WebKit view, not Safari, and runs no browser extension: since 21 October 2026 its Settings leaves the browser extension section out, and `AppShell` connects only the desktop app there. Sites are blocked by the extension in the browser in use; the desktop app blocks Mac apps, and since 21 October 2026 also blocked sites in Chrome (step 4, part 5).
- **Checked:** the build, conversion and signing (with the Apple ID's team since 21 October 2026, the extension inside too); the extension's 243 tests; connecting and blocking, in Safari (tried by the user 21 October 2026: a blocked site showed the blocked page).

## Step 4 so far: the desktop app

**Part 1, blocked apps (10 October 2026; committed 15 October as `413abbb`).** Built in the backend and web app first, because it needs neither Rust nor Xcode.
- **Backend:** migration `V18__create_blocked_apps.sql` (`name`, nullable `bundle_id`, `active`; unique per user and name). `BlockedApp`, `BlockedAppRepository`, `BlockedAppRequest`, `BlockedAppResponse` in `blocking/`; CRUD at `/api/blocked-apps` in `BlockingController`. An app is matched by bundle id when it has one, otherwise by name ignoring case; the same name in another case is a duplicate. Kept separate from `RuleTarget`, which is built around URL rules, and with no allowlist.
- **Same rule as sites:** `BlockingService` locks apps like block rules (add allowed while a session enforces; edit and delete refused with 409). Active apps are in `BlockingSnapshot` and `blocking-state` as `blockedApps`, and in the `stateVersion` fingerprint. The extension ignores the new field.
- **Backup:** export format **2.4** adds `blockedApps`; 2.0 to 2.3 still restore. Restore checks names, bundle ids and duplicates. Account and data deletion remove apps.
- **Web app:** a third **Apps** tab on Blocking (`AppForm.vue`, `AppList.vue`, `utils/blockedApps.ts`, the store's `addApp`, `updateApp` and `deleteApp`). Only the name is shown, following the UI preferences; the desktop app's picker is meant to fill bundle ids. An edit keeps the bundle id while the name is unchanged and drops it on a rename. The plan said "a Settings row", but the Blocking page is where the user already manages what is blocked. Checked in light and dark mode at 900 and 390 pixels.
- **Docs:** `technical_architecture.md` (endpoints, blocking state, backup format) and `requirements_specification.md` (no longer out of scope; Tauri chosen).
- **Tests:** `BlockedAppsApiIntegrationTest` (new), `BlockingServiceTest`, `ExtensionControllerTest`, `RestoreIntegrationTest`, `ExportServiceTest`; the web app's view, store and API tests.

**Part 2, the shell (15 October 2026, `ccf0807`).** `desktop/`, Tauri 2.12; `desktop/README.md` has how to build it and its files.
- **Decided by the user:** when FocusQuest already answers on 127.0.0.1:8080 (the Docker container), the app opens it and leaves it running on quit; only when nothing answers does it start its own copy. The two copies have separate data (the Docker volume, and `~/Library/Application Support/FocusQuest`) and cannot run at once. Offered and not taken: always its own copy (moving the data across once), or Docker only.
- **Finding FocusQuest:** a plain HTTP request to `/api/auth/setup-status`, which answers without sign-in; anything else on the port is reported as another program holding it.
- **Its own copy:** `scripts/prepare-backend.sh` builds the boot jar and a `jlink`ed JRE (a fixed module list, since `jdeps` cannot see into a boot jar's nested jars) into `src-tauri/resources/backend/`, bundled as a resource rather than a Tauri sidecar (a JRE is a folder, not one binary). Started with `FOCUSQUEST_DATA_DIR`, a JWT secret made once and kept in `jwt-secret` (0600) beside the data, bound to 127.0.0.1:8080, logging to `~/Library/Logs/FocusQuest/backend.log`; stopped with SIGTERM, then killed after 15 seconds.
- **Window and menu bar:** a local page (`ui/index.html`) says "Looking for", "Starting" or why it failed, through its query string, then the window navigates to http://127.0.0.1:8080/. The web app gets no Tauri IPC. Closing hides the window; the menu bar icon (a template image made from the favicon) has Open and Quit, and the Dock icon reopens it.
- **Checked:** the cut-down JRE runs the backend (fresh database migrated, web app served, clean shutdown on SIGTERM, 6 seconds); the built app (120 MB) found the Docker copy, started nothing and quit cleanly. Since tried live by the user: the window on Docker, and its own copy, which it started while Docker was down. `cargo test`: the probe's answer, the secret, the paths; `scripts/test-all.sh` runs it.
- The window is macOS's WebKit: its own sign-in, and no browser extension, so no website blocking in it. Not signed or notarised.

**Part 3, keeping blocked apps closed (15 October 2026, `47aca31`).**
- **Decided by the user:** the desktop app gets its own credential through its window (offered and not taken: a pairing code); a blocked app is asked to quit, as Cmd+Q does (not taken: hiding it, or force quitting).
- **Backend:** `ExtensionClient` (`BROWSER`, `DESKTOP`); migration `V19__add_client_to_extension_credentials.sql` makes credentials one per user and client (H2 hands the old unique index to the foreign key, so the key is dropped and added again). `POST` and `DELETE /api/auth/desktop-token`; `fqd_` tokens get `ROLE_DESKTOP`, allowed on `GET /api/extension/blocking-state` only, so the desktop app never checks in for sessions. Tests in `ExtensionTokenIntegrationTest`.
- **Desktop app:** `blocking.rs` keeps the token (0600), reads the blocking state every 3 seconds with `ureq`, keeps the last answer when FocusQuest cannot be reached and drops it when the token is refused; `apps.rs` lists running apps (not background-only ones) through `NSWorkspace` and calls `terminate` on the main thread; every second, matching apps are quit, with one notification per launch (`tauri-plugin-notification`). Never quit: itself, the Finder, the Dock, the login window, SystemUIServer. Three commands for the web app, allowed only from `http://127.0.0.1:8080/*` (`capabilities/web-app.json`, `build.rs`'s app manifest).
- **Web app:** `utils/desktopBridge.ts` (`@tauri-apps/api`), `api/desktopApi.ts`, `stores/desktopStore.ts` (as the extension's store, sharing `classify` and the opt-out, now per client), connected from `AppShell` on every signed-in page; a **Desktop app** row in Settings, shown only inside the app.
- **Checked:** `cargo test` (9, plus one reading this Mac's running apps), the backend's tests, the web app's (583); against the bundled backend on a spare port, a desktop token read the blocked apps and was refused on the heartbeat. Tried live by the user (Docker rebuilt): Calculator was quit while blocking was on. Found on the way: an app opened while Docker was down keeps its own copy on port 8080 after Docker comes back, and OrbStack's forwarding of 8080 then stayed broken until `orb restart docker`. Quit the app before starting Docker.
- Matching was by name alone until part 4 below.

**Part 5, websites in Chrome and opening at login (21 October 2026).** The user works in the desktop app for the semester, browses in Chrome only, and wanted the app itself to block websites. Offered: watching browser tabs (chosen), or `/etc/hosts` through a helper running as administrator (domains only, every browser). `websites.rs` ports the extension's matcher and precedence; `chrome.rs` reads tab ids and addresses with `osascript -l JavaScript` (Chrome 155 gives ids as text) and sets a blocked tab's address by id, so a window brought forward meanwhile cannot send the wrong tab away; `lib.rs` does this in the one-second loop while Chrome runs and something is blocked. The tab goes to the web app's new `/blocked` page (a standalone route, shown signed in or not), which shows, like the extension's, the task, the time left counting down and today's streak as a progress bar; the desktop app reads them from `GET /api/extension/current-session`, which `ROLE_DESKTOP` may now read, and passes them in the address. The Automation permission's state reaches Settings through `desktop_status`. `tauri-plugin-autostart` opens the app at login from its first run, with **Open at Login** in the menu bar menu. Not covered: Firefox and Safari (the extension still does those), and quitting the app ends blocking, as for apps.

**Part 4, picking apps (15 October 2026, `5a3c646`).** Inside the desktop app, the Apps tab's name field suggests this Mac's apps (a `<datalist>`, the user's preference for no extra panels), from a fourth command, `installed_apps`: the `.app` bundles in /Applications, /System/Applications and ~/Applications, one folder down too, read with `NSBundle`, plus the running ones, each bundle id once. `appRequest` sends the bundle id of an installed app with the typed name; an edit keeps the app's own while the name stays, so an app saved by name alone gets its id when edited in the desktop app. In a browser nothing changes. Tests: `utils/blockedApps.test.ts`, `AppForm` in `BlockingComponents.test.ts`, `apps.rs` (`cargo test -- --ignored` lists this Mac's apps).
- **View, Reload (Cmd+R)** added 15 October 2026: the window keeps the web app it loaded, so after Docker was rebuilt with a fix, the user's window still ran the old code (Calibrate stayed disabled) and had no way to reload short of restarting the app.
- `prepare-backend.sh` now makes the JRE writable: jlink's read-only licence files broke the second build.

## Later steps (planned)

**Step 3: every browser (days 5–8).**
*Built by 15 October 2026 except trying Safari in Safari; "Step 3 so far" says what was built and where it differs from this plan (Safari ships in its own small app, "FocusQuest for Safari", rather than inside the desktop app).*
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
- **Safari.** Convert the build with `xcrun safari-web-extension-converter`. It must ship inside a Mac app, so it goes into the step 4 bundle (changed: its own small app, by the user's choice).

**Step 4: Tauri desktop app (days 9–14).**
*Built on 15 October 2026; "Step 4 so far" says what was built and where it differs from this plan (the blocked apps live on the Blocking page rather than in Settings, and the backend is a bundled resource rather than a Tauri sidecar).*
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
