# FocusQuest browser extension

Blocks distracting websites while a FocusQuest focus session is being enforced. It is a Manifest V3
extension written in TypeScript, built for Chrome and the other Chromium browsers (Edge, Brave, Arc,
Opera) and for Firefox. The backend decides *whether* to block; the extension only enforces
what the backend tells it (see section 9 of `docs/technical_architecture.md`).

## How it works

```
 Spring Boot backend ──(bearer token)──►  service worker
   GET  /api/extension/blocking-state         │  every 30 s, on wake, on token change
   POST /api/extension/heartbeat              ▼
   GET  /api/extension/current-session   chrome.storage.local  ◄── token (handed over by the web app),
                                              │                last state, sync health
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
| `src/background/externalMessages.ts` | Receives the token from the web app (origin-checked) |
| `src/background/statusBadge.ts` | Toolbar badge: red `!` when not connected |
| `src/background/sessionStateSynchronizer.ts` | Heartbeat, then re-fetch only when `stateVersion` changed |
| `src/background/dynamicRulesManager.ts` | Turns rules into declarativeNetRequest rules |
| `src/background/blockingStateStore.ts` | Persists state and sync health |
| `src/background/navigationGuard.ts` | Backstop for what a URL regex cannot express |
| `src/blocking/ruleNormalizer.ts`, `urlMatcher.ts`, `rulePrecedence.ts` | Port of the backend's reference implementation |
| `src/pages/blocked/` | The page shown in place of a blocked site |
| `src/pages/popup/` | Toolbar popup: connection state and a link to Settings |

### Rule semantics

Reproduces the backend (`RuleNormalizer`, `UrlRule`, `TargetUrl`, `RulePrecedence`):

- `youtube.com` blocks the domain and every subdomain; `youtube.com/shorts` blocks that path and everything below it (`/shorts/abc`, `/shorts?x=1`), but not `/shortsfoo`.
- The most specific matching rule wins (more host labels, then more path segments). An allowlist rule wins a tie. So with block `example.com` and allow `example.com/docs`, `example.com/docs/setup` loads and `example.com/forum` is blocked.
- Only top-level page navigations are blocked. Embedded frames and sub-resources are left alone.

### When the backend is unreachable or the token is rejected

Blocking **stays on**. The last known rules keep applying, and the blocked page says why it may be out of
date. A revoked token or a stopped backend is therefore not a way out of a session. The flip side: if
the token is revoked (say, by pressing **Disconnect**) and the session then ends, the extension cannot
learn that until it is connected again. The extension token itself does not expire, so a long session
never causes this on its own.

## Build and unit tests

```bash
cd extension
npm install
npm run build          # type-checks, then bundles into extension/dist (Chromium browsers)
npm run build:firefox  # the same into extension/dist-firefox
npm test               # unit tests (Vitest)
npm run watch          # rebuild on change
```

The Firefox build differs only in its manifest (`toFirefox` in `scripts/build.mjs`): the background
script runs as a background page, since Firefox has no extension service workers, and the id and
minimum version (140) are set under `browser_specific_settings`.

## Load the extension in Chrome

1. `cd extension && npm install && npm run build`
2. Open `chrome://extensions` and switch on **Developer mode** (top right).
3. Click **Load unpacked** and choose the **`extension/dist`** folder, **not** `extension/` itself.
   (`extension/` holds the TypeScript source, so Chrome reports "Could not load background script ''"
   and "Could not load manifest". Only `dist` contains the built files. If `dist` is missing, run
   `npm run build`.)
4. **FocusQuest** appears in the list. After changing code, run `npm run build` and press the reload
   icon on its card.

Edge, Brave, Arc and Opera load the same `dist` folder the same way, from their own extensions page
(`edge://extensions`, `brave://extensions`, and so on).

The extension talks to the backend using its host permissions, so its id does not need to be added to
the backend's CORS allow-list (`focusquest.cors.allowed-origins` in `application.yml`). Firefox and
Safari give each install a random id; the backend admits any of theirs on `/api/extension/**` only
(`CorsConfig`).

## Load the extension in Firefox

1. `cd extension && npm install && npm run build:firefox`
2. Open `about:debugging#/runtime/this-firefox` and click **Load Temporary Add-on…**.
3. Choose **`extension/dist-firefox/manifest.json`**.
4. After changing code, run `npm run build:firefox` and press **Reload** on its card.

A temporary add-on is removed when Firefox quits; load it again after a restart. Firefox installs
for good only add-ons Mozilla has signed (`npm run package:firefox` makes the zip to submit), or any
add-on in Firefox Developer Edition or Nightly with `xpinstall.signatures.required` set to `false` in
`about:config`.

`npm run lint:firefox` checks the build with Mozilla's linter. It warns once about Firefox for
Android, which FocusQuest does not target.

**Private windows:** Firefox runs no extension in a private window unless allowed. To block there
too, open the add-on in `about:addons` and set **Run in Private Windows** to **Allow** (Chrome's
equivalent is **Allow in Incognito** on the extension's details page).

## Sign the extension in

You do nothing beyond having the web app open once.

1. Start the app (`cd backend && ./gradlew bootRun`), open http://localhost:8080 (or the dev server,
   http://localhost:5173), and sign in to the web app. (First run? Create the account there.)
2. That's it. When the web app loads and finds the extension installed but not connected, it asks the
   backend for an extension token and hands it to the extension. The toolbar badge clears and the
   popup says **Connected**.

The web app's **Settings** page shows the state and has **Connect extension**, **Reconnect** and
**Disconnect** buttons. Disconnecting revokes the token, and the app then stops reconnecting on its
own until you press **Connect extension** again.

How it works, and why it is safe to leave connected:

- The extension token is *not* the web app's login token. It is a separate random token that the
  backend stores only as a hash. It never expires, survives backend restarts, and can only call the
  three `/api/extension` endpoints: it cannot start sessions, read history or delete data.
  Reconnecting replaces it, and disconnecting or deleting all data revokes it. The desktop app has a
  token of its own (`fqd_`), so connecting either never signs the other out.
- The web app reaches the extension through a small content script, the bridge
  (`src/content/webAppBridge.ts`), which runs only on the web app's origins: `http://localhost:8080`,
  `http://127.0.0.1:8080`, the dev server's `http://localhost:5173`, and the address set on the options
  page, if any (see below). The page posts its request with
  `window.postMessage`, the bridge passes it to the service worker and posts the answer back. This works
  in every browser, unlike Chrome's `externally_connectable`, which it replaces. The service worker checks
  the page's origin again and accepts only tokens starting `fqx_`.
- The bridge marks the page (`data-focusquest-extension` on `<html>`), so the web app knows at once
  whether an extension is listening. When the extension is installed or reloaded, it adds the bridge to
  web app tabs that are already open, so they need no reload.

### FocusQuest on another port or computer

The extension expects FocusQuest at http://localhost:8080. If yours runs elsewhere (another
`FOCUSQUEST_PORT`, or another computer), click the toolbar icon, then **Change the FocusQuest address**
(or open the extension's options from the browser's extensions page). Type the address, for example
`localhost:9000` or `http://mymac.local:8080`, and save. Leave it empty to go back to this computer.

- One address covers everything, because the backend serves both its API and the web app: the
  extension calls the API there, runs the bridge there, and links there from the popup and the
  blocked page.
- Saving a new address signs the extension out, so its token is never sent to another host. Open
  FocusQuest at the new address and it connects the extension again by itself.
- Blocking stays as it was last synchronized until the new backend says otherwise, so changing the
  address does not end blocking during a session.
- The service worker registers the bridge for the new address at run time
  (`src/background/bridgeRegistration.ts`). Reload a FocusQuest tab that was already open there.

The toolbar badge shows the state at a glance: nothing when all is well, a red **!** when the
extension is not connected (sites are not blocked until it is), and a grey **?** when it cannot reach
the backend. Clicking the icon explains and links to Settings.

Check the connection from the service worker console (`chrome://extensions` → **service worker**):

```js
(await chrome.storage.local.get('syncHealth')).syncHealth   // { status: 'ok', ... }
```

`status` is `ok`, `signed-out` (no token), `unauthorized` (token rejected, for example revoked),
`offline` (backend not reachable) or `error`.

## Test the blocking behavior

Use the web app (http://localhost:8080) to create and start the session (step 2).

1. **Add rules.** Use the web app's **Blocking** page, or the backend API as below. Log in
   with curl to get a token (the same one the web app uses), put it in a shell variable, then add
   block rules and allowlist rules:

   ```bash
   TOKEN=$(curl -s -X POST http://127.0.0.1:8080/api/auth/login \
     -H 'Content-Type: application/json' \
     -d '{"username":"YOUR_USER","password":"YOUR_PASSWORD"}' | sed 's/.*"token":"\([^"]*\)".*/\1/')
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
3. **Starting it in the web app syncs the extension at once.** A change made straight against the API
   instead waits for the next 30-second sync; trigger it right away with the reload icon on the
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
8. **Complete** the session (or abandon it and meet the daily target). Within a second or two the rules
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
| `storage` | Extension token, synchronized state, sync health, the FocusQuest address |
| `alarms` | The 30-second sync, which survives the worker being shut down |
| `scripting` | Adds the bridge to web app tabs already open when the extension is installed or reloaded, and runs it on the address set on the options page |

`content_scripts` runs the bridge on the web app's default origins only (`WEB_APP_ORIGINS` in
`src/utils/config.ts`, which must match), which is how the web app hands over the token; another
address is added at run time. Firefox's match patterns cannot name a port, so there the bridge runs on
every port of those hosts and checks the page's origin itself before it marks the page. The manifest's
`options_ui` is the options page with the FocusQuest address. The manifest's
`action` adds the toolbar button, badge and popup.

`web_accessible_resources` exposes only the blocked page, to http(s) pages, because a redirect from a
website to an extension page requires it. The bridge is the only content script and runs only on the
web app's own pages. It keeps nothing, and page scripts cannot reach its isolated world, so the token
is never reachable from website code.

## Follow-up work

The full, numbered list lives in `docs/technical_architecture.md`, section 9 ("Extension follow-up
work"). The ones that matter first:

1. **Extension icons.**
2. **Token expiry.** The extension token never expires; consider expiry with silent renewal if the API
   ever leaves localhost.
3. **Login form in the popup**, so connecting does not need the web app to be running.

Also open: aligning the backend with the extension on malformed
percent-escapes, and non-ASCII path rules being enforced only by the navigation guard.
