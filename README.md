# FocusQuest

A local-first focus app: a Spring Boot backend, a Vue web app and a Chrome extension that blocks
distracting sites during a focus session. Design documents are in [`docs/`](docs/).

| Part | Folder | Stack |
| --- | --- | --- |
| Backend API | [`backend/`](backend/) | Java 21, Spring Boot 4, H2, Flyway, JWT |
| Web app | [`frontend/`](frontend/README.md) | Vue 3, TypeScript, Vite, Pinia |
| Chrome extension | [`extension/`](extension/README.md) | Manifest V3, TypeScript |

## Prerequisites

- JDK 21 (Gradle is bundled as `./gradlew`)
- Node.js 20 or newer, with npm
- Chrome 120 or newer, to use the extension

## Run the full application locally

Use three terminals, from the repository root.

**1. Backend** (http://127.0.0.1:8080)

```bash
export FOCUSQUEST_JWT_SECRET="$(openssl rand -base64 48)"   # optional, see below
cd backend
./gradlew bootRun
```

The data lives in `backend/data/` (an H2 file database), so it survives restarts.

`FOCUSQUEST_JWT_SECRET` signs sign-in tokens. If you do not set it, the backend generates a random
secret each run and everyone is signed out whenever it restarts. Set it (at least 32 bytes) to stay
signed in across restarts. Never commit it.

Optional: `FOCUSQUEST_H2_CONSOLE=true` enables the H2 database console at
http://127.0.0.1:8080/h2-console (JDBC URL `jdbc:h2:file:./data/focusquest`, user `sa`, empty
password). It is off by default because it exposes the whole database.

**2. Web app** (http://localhost:5173)

```bash
cd frontend
npm install
npm run dev
```

The port is fixed: the backend only allows CORS from `http://localhost:5173`. On first launch the
app shows account setup; afterwards it shows login. To point at another backend, copy
`frontend/.env.example` to `frontend/.env.local` and edit `VITE_API_BASE_URL`.

**3. Chrome extension** (optional)

```bash
cd extension
npm install
npm run build
```

Then load `extension/dist` in `chrome://extensions` (Developer mode, **Load unpacked**) and sign it
in. The full steps, including how to add sites to block, are in [`extension/README.md`](extension/README.md).

## Run the tests

Everything at once (backend, frontend and extension, with type-checks):

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
| End-to-end | `cd e2e && npm test` | Playwright: web app, backend and extension together in Chromium. Setup in [`e2e/README.md`](e2e/README.md) |

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

- **Port 8080 or 5173 already in use**: stop the other process. Both ports are fixed.
- **Signed out after restarting the backend**: set `FOCUSQUEST_JWT_SECRET` (see above).
- **Reset all data**: use Settings in the web app, or stop the backend and delete `backend/data/`.
