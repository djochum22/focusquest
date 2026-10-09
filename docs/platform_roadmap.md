# Platform work: where it stands

This note is for picking the work up again, for example in a new Claude session. It covers four things:
- running FocusQuest without VS Code
- running it in a container
- blocking in every browser
- a desktop app that also blocks Mac applications

The camera work has its own note, `docs/camera_roadmap.md`.

## Start here

**Where things stand (9 October 2026).** Steps 1 and 2 are committed. The user checked both by hand: step 1 under `bootRun`, step 2 with `docker compose up` on :8080 (account setup, the extension connected and blocking, data and sign-in surviving `docker compose restart`). OrbStack is installed (if `docker info` fails, `orb start`), and the user's `.env` holds a generated secret.

**Under way: step 3, every browser.** Start with the bridge (see "Later steps", step 3).

`.vscode/settings.json` stays out of the commits: VS Code's Java extension changed it on its own.

The extension's backend address in an options page moved to step 3 (decided 9 October 2026). `externally_connectable` is fixed in the manifest, so an extension pointed elsewhere could not be connected from the web app until step 3's content-script bridge replaces it, and with each computer running its own copy on 127.0.0.1:8080 nothing needs it yet.

`.vscode/settings.json` was left out of the commit: VS Code's Java extension changed it on its own (`interactive` to `automatic`).

**Also waiting on the user: camera labelling** (C1d; see `docs/camera_roadmap.md`, "To do later, by the user: label"). It takes 20 to 30 minutes, and no camera code can go on until it is done. Plan: do it in the first days, alongside this work. C1d step 2 (the `accuracy` command) comes after the desktop app, or whenever the labels are in.

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
| 3 | Every browser: a bridge without `externally_connectable`, a Firefox build, Safari | **Next** |
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

## Later steps (planned)

**Step 3: every browser (days 5–8).**
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
