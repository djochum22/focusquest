# End-to-end tests

Playwright tests that drive the whole product the way a user does: the Vue web app, the real Spring
Boot backend and the Chrome extension, together in Chromium.

## Run them

```bash
cd extension && npm install && cd ..     # the suite builds the extension from source
cd e2e
npm install
npm run install-browser                  # once: Playwright's Chromium (about 100 MB)
npm test                                 # or: npm run test:headed, to watch
```

`npm test` starts everything it needs and stops it afterwards. The first run takes a minute or two
while Gradle starts the backend. After a failure, `npx playwright show-report` opens the report,
with a trace of each failed test.

## What runs where

Everything has its own port, so the suite can run while the development servers (8080 and 5173) are
up, and it never touches `backend/data/`.

| Part | Address | Started by |
| --- | --- | --- |
| Backend | http://127.0.0.1:18080 | `./gradlew bootTestRun --args=--spring.profiles.active=e2e` |
| Web app | http://localhost:15173 | `npm run dev -- --port 15173`, pointed at the backend above |
| A site to block | http://distraction.example:15180 | `support/siteServer.mjs` on 127.0.0.1 |
| Extension | loaded into Chromium | built by `support/globalSetup.ts` into `.extension/` |

- **The backend** runs from the test sources (`backend/src/test/java/com/example/e2e`) with the `e2e`
  profile (`backend/src/test/resources/application-e2e.yml`): an in-memory database, and two hooks the
  real application does not have. `POST /api/e2e/reset` wipes the database and sets the clock to 09:00
  UTC; `POST /api/e2e/clock/advance?seconds=N` moves the clock forward, so a five-minute session can be
  completed at once. The heartbeat timeout is raised to 24 hours, so moving the clock is not mistaken
  for the browser going quiet (interruption has its own backend tests).
- **The extension** is built with `FOCUSQUEST_BACKEND_URL`, `FOCUSQUEST_FRONTEND_URL` and
  `FOCUSQUEST_OUT_DIR` (see `extension/scripts/build.mjs`), so it talks to the ports above.
- **The blocked site** is a made-up host. Chromium resolves it to 127.0.0.1 through a host-resolver
  rule, so no test depends on the internet.

Before each test the backend is reset and Chromium starts with a fresh profile, so each test begins
at first-launch setup with a new, unconnected extension. Tests run one at a time: there is one
backend and it allows one account.

While you work on the tests, leave the servers running between runs: locally, Playwright reuses
anything already listening on those ports (on CI it always starts its own).

## What is covered

`tests/sessions.spec.ts`:

- First-launch setup, the extension connecting itself, a block rule, and a session that blocks the
  site while running and paused, then releases it on completion and awards XP.
- Abandoning below the daily target keeps the site blocked until a manual override, which is
  recorded with its XP penalty.
- Rules cannot be loosened while a session runs, but can be tightened.

Each blocking check allows 10 seconds, less than the extension's 30-second check-in, so the tests
also show that the web app tells the extension about changes straight away.
