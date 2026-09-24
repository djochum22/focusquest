# FocusQuest Chrome extension

Blocks distracting websites while a FocusQuest focus session is being enforced. It is a Manifest V3
extension written in TypeScript. The backend decides *whether* to block; the extension only enforces
what the backend tells it (see section 9 of `docs/technical_architecture.md`).

## How it works

```
 Spring Boot backend ──(bearer token)──►  service worker
   GET  /api/extension/blocking-state         │  every 30 s, on wake, on token change
   POST /api/extension/heartbeat              ▼
   GET  /api/extension/current-session   chrome.storage.local  ◄── token, last state, sync health
                                              │
                          ┌───────────────────┴───────────────────┐
                          ▼                                       ▼
        declarativeNetRequest dynamic rules            navigation guard (webNavigation)
        (browser enforces; works while the             exact matcher + precedence; catches
         worker sleeps; redirects to blocked page)     SPA navigations and odd URL spellings
                          └───────────────────┬───────────────────┘
                                              ▼
                                      blocked page (shows session + streak)
```

| Path | Role |
| --- | --- |
| `manifest.json` | Permissions, service worker, blocked-page exposure |
| `src/background/serviceWorker.ts` | Entry point: registers listeners, alarm, first sync |
| `src/background/backendClient.ts` | Bearer-token client for the three extension endpoints |
| `src/background/sessionStateSynchronizer.ts` | Heartbeat, then re-fetch only when `stateVersion` changed |
| `src/background/dynamicRulesManager.ts` | Turns rules into declarativeNetRequest rules |
| `src/background/blockingStateStore.ts` | Persists state and sync health |
| `src/background/navigationGuard.ts` | Backstop for what a URL regex cannot express |
| `src/blocking/ruleNormalizer.ts`, `urlMatcher.ts`, `rulePrecedence.ts` | Port of the backend's reference implementation |
| `src/pages/blocked/` | The page shown in place of a blocked site |

### Rule semantics

Reproduces the backend (`RuleNormalizer`, `UrlRule`, `TargetUrl`, `RulePrecedence`):

- `youtube.com` blocks the domain and every subdomain; `youtube.com/shorts` blocks that path and everything below it (`/shorts/abc`, `/shorts?x=1`), but not `/shortsfoo`.
- The most specific matching rule wins (more host labels, then more path segments). An allowlist rule wins a tie. So with block `example.com` and allow `example.com/docs`, `example.com/docs/setup` loads and `example.com/forum` is blocked.
- Only top-level page navigations are blocked. Embedded frames and sub-resources are left alone.

### When the backend is unreachable or the token has expired

Blocking **stays on**. The last known rules keep applying, and the blocked page says why it may be out of
date. An expired token or a stopped backend is therefore not a way out of a session. The flip side: if
the token expires *and* the session then ends, the extension cannot learn that until it is signed in
again (see "Follow-up work").

## Build and unit tests

```bash
cd extension
npm install
npm run build        # type-checks, then bundles into extension/dist
npm test             # unit tests (Vitest)
npm run watch        # rebuild on change
```

## Load the extension in Chrome

1. `cd extension && npm install && npm run build`
2. Open `chrome://extensions` and switch on **Developer mode** (top right).
3. Click **Load unpacked** and choose the **`extension/dist`** folder, **not** `extension/` itself.
   (`extension/` holds the TypeScript source, so Chrome reports "Could not load background script ''"
   and "Could not load manifest". Only `dist` contains the built files. If `dist` is missing, run
   `npm run build`.)
4. **FocusQuest** appears in the list. After changing code, run `npm run build` and press the reload
   icon on its card.

The extension talks to the backend using its host permissions, so its id does not need to be added to
the backend's CORS allow-list (`focusquest.cors.allowed-origins` in `application.yml`).

## Sign the extension in

There is no login screen in the extension yet. It needs the same JWT the web app uses, in
`chrome.storage.local` under the key `token`.

1. Start the backend (`cd backend && ./gradlew bootRun`) and get a token:

   ```bash
   curl -s -X POST http://127.0.0.1:8080/api/auth/login \
     -H 'Content-Type: application/json' \
     -d '{"username":"YOUR_USER","password":"YOUR_PASSWORD"}'
   ```

   Copy the `token` value from the response. (First run? Create the account in the web app.)

2. On `chrome://extensions`, click **service worker** on the FocusQuest card. This opens DevTools for
   the worker. In its **Console**, run:

   ```js
   chrome.storage.local.set({ token: 'PASTE_TOKEN_HERE' })
   ```

Writing the token triggers an immediate sync. The token expires after 60 minutes
(`focusquest.jwt.expiration-minutes`); repeat both steps with a fresh one when it does. Unless
`FOCUSQUEST_JWT_SECRET` is set, restarting the backend invalidates it too.

Check the connection at any time from the same console:

```js
(await chrome.storage.local.get('syncHealth')).syncHealth   // { status: 'ok', ... }
```

`status` is `ok`, `signed-out` (no token), `unauthorized` (token rejected or expired), `offline`
(backend not reachable) or `error`.

## Test the blocking behavior

Use the web app (`npm run dev` in `frontend/`, http://localhost:5173) to create and start the session (step 2).

1. **Add rules.** The web app has no screen for this yet (see "Follow-up work"), so use the backend
   API. Put your token in a shell variable, then add block rules and allowlist rules:

   ```bash
   TOKEN='PASTE_TOKEN_HERE'
   add() { curl -s -X POST "http://127.0.0.1:8080/api/$1" -H "Authorization: Bearer $TOKEN" \
             -H 'Content-Type: application/json' -d "{\"targetValue\":\"$2\"}"; echo; }

   add blocked-targets youtube.com
   add blocked-targets reddit.com/r/all
   add allowlist-targets youtube.com/feed/subscriptions
   ```

   Rules are a domain (`youtube.com`) or a domain plus path (`youtube.com/shorts`), with no `https://`
   and no `?query`. List them with `GET /api/blocked-targets` and `GET /api/allowlist-targets`, and
   remove one with `DELETE /api/blocked-targets/{id}`. Rules cannot be loosened while a session is
   being enforced.
2. **Start a session** in the web app (create one, then start it). Nothing is blocked until it starts.
3. **Wait up to 30 seconds** for the next sync, or trigger it right away with the reload icon on the
   extension's card (the worker syncs whenever it starts). Then confirm the rules arrived, in the worker console:

   ```js
   await chrome.declarativeNetRequest.getDynamicRules()
   ```

4. **Check each behavior** by visiting these in a tab:

   | Visit | Expected |
   | --- | --- |
   | `https://youtube.com` | Blocked page |
   | `https://m.youtube.com/watch?v=abc` | Blocked page (subdomain) |
   | `https://notyoutube.com` | Loads normally |
   | `https://reddit.com/r/all/top` | Blocked page (child path) |
   | `https://reddit.com/r/programming` | Loads normally (other path) |
   | `https://www.youtube.com/feed/subscriptions` | Loads (allowlist beats the broader block) |
   | `https://reddit.com/r/%41ll` or `https://reddit.com//r/all` | Blocked page (odd spellings of a blocked path) |

   The blocked page shows the site, the rule that matched, your task, the time remaining and today's
   streak progress. Its **Go back** button leaves it.

5. **Ask Chrome directly** which rule applies to a URL (worker console). An empty `matchedRules` means
   nothing blocks it at the network level:

   ```js
   await chrome.declarativeNetRequest.testMatchOutcome({ url: 'https://youtube.com/watch', type: 'main_frame' })
   ```

6. **Single-page navigation.** With a path rule such as `youtube.com/shorts` and *no* domain rule,
   open `https://youtube.com`, then click into a Short. The page never reloads, so only the navigation
   guard can catch it; you should land on the blocked page.

7. **Pause and resume** the session. Sites stay blocked while paused (the blocked page says so).
8. **Complete** the session (or abandon it and meet the daily target). Within 30 seconds the rules
   disappear (`getDynamicRules()` returns `[]`) and the sites load again. An **abandoned** session
   keeps blocking until the daily target is reached or you use the manual override in the web app.
9. **Fail-closed check.** During an active session, stop the backend. Sites must stay blocked, and a
   freshly opened blocked page says it cannot reach the backend. Restart the backend and the health
   returns to `ok`.
10. **Restart check.** During an active session, quit Chrome completely and reopen it. Blocking should
    be in force immediately, before any sync, because Chrome keeps the rules.

### Reading logs

Logs are prefixed `[FocusQuest]` in the service worker console (`chrome://extensions` → **service
worker**). Tokens are never logged.

## Permissions

| Permission | Why |
| --- | --- |
| `declarativeNetRequest` | Block by redirecting page navigations to the blocked page |
| `host_permissions: <all_urls>` | Chrome only allows a redirect rule on sites the extension has access to, and this also lets the worker call the local backend |
| `webNavigation` | The navigation guard (single-page navigations, URL spellings) |
| `storage` | Token, synchronized state, sync health |
| `alarms` | The 30-second sync, which survives the worker being shut down |

`web_accessible_resources` exposes only the blocked page, to http(s) pages, because a redirect from a
website to an extension page requires it. The extension has no content scripts, so the token is never
reachable from website code.

## Follow-up work

The full, numbered list lives in `docs/technical_architecture.md`, section 9 ("Extension follow-up
work"). The ones that matter first:

1. **No sign-in UI.** The token is pasted in by hand (see above). Needs a login form in the extension,
   or a token handoff from the web app.
2. **Token lifetime.** The JWT lasts 60 minutes, so a session longer than that outlives the
   extension's sign-in: blocking stays on, but the extension can no longer see the session end. Needs
   refresh tokens or a longer-lived extension credential.
3. **Signed-out state is only visible on the blocked page.** Needs a toolbar badge or popup.
4. **No screen for choosing blocked and allowed sites** in the web app. The backend endpoints exist
   (`/api/blocked-targets`, `/api/allowlist-targets`); the Vue app has no UI for them yet.

Also open: sync latency of up to 30 seconds (no push from the web app), no extension icons, an
end-to-end test suite, aligning the backend with the extension on malformed percent-escapes, and
non-ASCII path rules being enforced only by the navigation guard.
