# FocusQuest frontend

Vue 3 + TypeScript + Vite, with Vue Router, Pinia and Axios. It talks to the Spring Boot backend
in `../backend` (default `http://127.0.0.1:8080`).

## Commands

```bash
npm install        # install dependencies
npm run dev        # dev server at http://localhost:5173
npm run build      # type-check (vue-tsc) and build to dist/
npm run preview    # serve the production build locally
npm test           # run the Vitest suite once (npm run test:watch to watch)
```

The backend must be running for login and setup to work (`cd ../backend && ./gradlew bootRun`).
It allows CORS from `http://localhost:5173` only, so the dev server is pinned to that port.

## Configuration

`VITE_API_BASE_URL` overrides the backend URL. Copy `.env.example` to `.env.local` to set it.

## Structure

```
src/
  api/          Axios client (attaches the bearer token) and one module per backend area
  components/   Presentational components; common/ holds shared form and button pieces
  router/       Routes and the navigation guard
  stores/       Pinia stores (authStore owns the token and signed-in user)
  types/        TypeScript types mirroring the backend DTOs
  utils/        Validation and token storage helpers
  views/        One component per route
```

## Authentication

- On opening `/login` or `/setup`, the app calls the public `GET /api/auth/setup-status` and shows
  setup until the account exists, then login. If the backend is unreachable the requested page
  loads and shows the connection error.
- `/setup` creates the single local account and signs the user in.
- `/login` exchanges credentials for a JWT. The token is kept in `sessionStorage`, so it is
  dropped when the tab closes.
- Every route is protected unless it has `meta: { public: true }`. Unauthenticated users are sent
  to `/login` and returned to the page they wanted afterwards.
- A 401 on a request that carried a token (expired or invalid) signs the user out and redirects
  to `/login`.
