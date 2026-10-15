# FocusQuest

A local-first focus app: a Spring Boot backend, a Vue web app and a browser extension that blocks
distracting sites during a focus session, a Mac app that keeps distracting apps closed, and an
optional camera companion program that checks you stay on task. Design documents are in [`docs/`](docs/).

| Part | Folder | Stack |
| --- | --- | --- |
| Backend API | [`backend/`](backend/) | Java 21, Spring Boot 4, H2, Flyway, JWT |
| Web app | [`frontend/`](frontend/README.md) | Vue 3, TypeScript, Vite, Pinia |
| Browser extension | [`extension/`](extension/README.md) | Manifest V3, TypeScript; Chromium browsers, Firefox and Safari |
| Camera companion | [`vision/`](vision/README.md) | Python 3.11+; OpenCV and MediaPipe for the camera |
| Desktop app (macOS) | [`desktop/`](desktop/README.md) | Tauri v2, Rust; bundles the backend with a cut-down JRE |

## Prerequisites

- JDK 21 (Gradle is bundled as `./gradlew`)
- Node.js 20 or newer, with npm
- For the extension: Chrome 120 or newer (or another Chromium browser), Firefox 140 or newer, or
  Safari 17 or newer (building for Safari needs Xcode)
- Python 3.11 or newer, for the camera companion and its tests
- For the desktop app: Rust (https://rustup.rs) and macOS 12 or newer

## Run the full application locally

**1. The app** (http://localhost:8080)

```bash
export FOCUSQUEST_JWT_SECRET="$(openssl rand -base64 48)"   # optional, see below
cd backend
./gradlew bootRun
```

This builds the web app and serves it from the backend, so the whole app is on one address. The
first run also installs the web app's npm packages. The data lives in `backend/data/` (an H2 file
database), so it survives restarts. `./gradlew bootJar` builds the same thing as one jar
(`build/libs/`), which runs with `java -jar` from any folder.

| Variable | Default | What it does |
| --- | --- | --- |
| `FOCUSQUEST_JWT_SECRET` | random each run | Signs sign-in tokens (see below) |
| `FOCUSQUEST_PORT` | `8080` | The port |
| `FOCUSQUEST_BIND` | `127.0.0.1` | The address it listens on; this computer only by default |
| `FOCUSQUEST_DATA_DIR` | `./data` | Where the database is kept |

`FOCUSQUEST_JWT_SECRET` signs sign-in tokens. If you do not set it, the backend generates a random
secret each run and everyone is signed out whenever it restarts. Set it (at least 32 bytes) to stay
signed in across restarts. Never commit it.

Optional: `FOCUSQUEST_H2_CONSOLE=true` enables the H2 database console at
http://127.0.0.1:8080/h2-console (JDBC URL `jdbc:h2:file:./data/focusquest`, user `sa`, empty
password). It is off by default because it exposes the whole database.

On first launch the app shows account setup; afterwards it shows login.

**2. Web app dev server** (optional, http://localhost:5173, only while working on the web app)

```bash
cd frontend
npm install
npm run dev
```

It reloads on every change and calls the backend on :8080. The port is fixed: the backend only
allows CORS from `http://localhost:5173`. To point at another backend, copy `frontend/.env.example`
to `frontend/.env.local` and edit `VITE_API_BASE_URL`.

**3. Browser extension** (optional)

```bash
cd extension
npm install
npm run build            # Chrome, Edge, Brave, Arc, Opera: extension/dist
npm run build:firefox    # Firefox: extension/dist-firefox
```

In a Chromium browser, load `extension/dist` from its extensions page (Developer mode, **Load
unpacked**). In Firefox, load `extension/dist-firefox/manifest.json` from `about:debugging` (**This
Firefox**, **Load Temporary Add-on**). The web app connects it on its own. The full steps, including how to add sites to block, are in [`extension/README.md`](extension/README.md).

**4. Camera companion** (optional)

With a webcam it checks that you are at your desk, looking at your work and not holding a phone;
it can also replay a scripted timeline, to try the warnings out. Turn camera verification on in Settings, pair, and run it; see
[`vision/README.md`](vision/README.md).

## Run with Docker

The same app in a container, with nothing else to install: no JDK, Node or Gradle. It runs on
http://localhost:8080 and is reachable from this computer only. Any Docker runtime works; on a Mac,
[OrbStack](https://orbstack.dev) (`brew install orbstack`) is light and quick.

```bash
cp .env.example .env    # then set FOCUSQUEST_JWT_SECRET in it: openssl rand -base64 48
docker compose up -d --build
```

| Command | What it does |
| --- | --- |
| `docker compose logs -f` | Follow the backend's log |
| `docker compose down` | Stop it; the data stays |
| `docker compose up -d --build` | Rebuild after pulling changes, and start it |

The database is kept in the Docker volume `focusquest_focusquest-data`, not in `backend/data/`, so the
container and `./gradlew bootRun` each have their own data. Run only one of them at a time: both
want port 8080. The extension and the camera companion connect to it exactly as they do to
`bootRun`. The companion runs outside the container, since Docker on macOS cannot reach the camera.

**Back up** with Export in Settings, or copy the whole volume (stop the app first, so the database
is not written to while it is copied):

```bash
docker compose stop
docker run --rm -v focusquest_focusquest-data:/data -v "$PWD":/backup alpine \
  tar czf /backup/focusquest-data.tar.gz -C /data .
docker compose start
```

To restore, stop the app and unpack it into the volume the same way: `tar xzf
/backup/focusquest-data.tar.gz -C /data`.

## Run the desktop app (macOS)

FocusQuest in its own window with a menu bar icon, which also quits your blocked Mac apps (the
**Apps** tab on the Blocking page) while blocking is on. If FocusQuest is already running on
port 8080 (Docker or `bootRun`) it uses that; otherwise it starts the copy bundled inside it, with
its own data in `~/Library/Application Support/FocusQuest`.

```bash
cd desktop
npm install
npm run build
open src-tauri/target/release/bundle/macos/FocusQuest.app
```

Sign in inside its window once: it connects itself, and Settings shows **Desktop app: Connected**.
Quit the app before starting Docker, or the app's own copy keeps port 8080. More in
[`desktop/README.md`](desktop/README.md).

## Run the tests

Everything at once (backend, frontend and extension, with type-checks, the camera companion, and the
desktop app where Rust is installed):

```bash
./scripts/test-all.sh
FOCUSQUEST_E2E=1 ./scripts/test-all.sh   # also the end-to-end suite
```

Or each suite on its own:

| Suite | Command | Notes |
| --- | --- | --- |
| Backend, all | `cd backend && ./gradlew test` | Uses in-memory databases; nothing touches `backend/data/` |
| Backend, one class | `./gradlew test --tests '*SecurityConfigurationIntegrationTest'` | |
| Backend, integration only | `./gradlew test --tests 'com.example.focusquest.integration.*'` | |
| Frontend | `cd frontend && npm test` | Vitest; `npm run test:watch` while developing |
| Frontend type-check | `cd frontend && npx vue-tsc -b` | `npm run build` also runs it |
| Extension | `cd extension && npm test` | Vitest; `npm run typecheck` for types |
| Camera companion | `cd vision && PYTHONPATH=src:tests python3 -m unittest discover -s tests` | Standard library only; nothing to install |
| Desktop app | `cd desktop/src-tauri && cargo test` | `cargo test -- --ignored` also reads this Mac's apps |
| End-to-end | `cd e2e && npm test` | Playwright: web app, backend, extension and camera companion together in Chromium. Setup in [`e2e/README.md`](e2e/README.md) |

Gradle skips a test task whose inputs have not changed. Add `--rerun-tasks` to force a full run.
The HTML report of a backend run is `backend/build/reports/tests/test/index.html`.

### What the backend integration tests cover

They live in `backend/src/test/java/com/example/focusquest/integration/` and extend
`support/ApiIntegrationTest`, which boots the whole application (real security filter chain,
services, Flyway-migrated in-memory database) and drives it over HTTP with MockMvc. Each test starts
from an empty database and a frozen clock that the test moves forward. These tests share one
database, so they must not run in parallel.

- `SessionWorkflowApiIntegrationTest`: setup, sessions from start to completion, abandon and
  override, error responses, export and deletion.
- `StreakCalculationApiIntegrationTest`: daily and weekly streaks, missed periods, time zones, time
  split across midnight and the start of the week.
- `ProfileApiIntegrationTest`: editing the profile, and a time-zone change taking effect from the
  next day and week.
- `SecurityConfigurationIntegrationTest`: authentication on every route, token attacks, CORS,
  headers, and that no secret reaches the logs.

## Troubleshooting

- **Port 8080 already in use**: stop the other process, or set `FOCUSQUEST_PORT` (the extension
  expects 8080). The dev server's 5173 is fixed.
- **Signed out after restarting the backend**: set `FOCUSQUEST_JWT_SECRET` (see above).
- **Reset all data**: use Settings in the web app, or stop the backend and delete `backend/data/`
  (with Docker: `docker compose down -v` deletes the volume).
