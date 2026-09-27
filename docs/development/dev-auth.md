# Dev Auth (local development sign-in)

> **Scope:** local development only. Never enable or expose this in production.
> Introduced for `AUTH-UNPROTECTED-FIX-002` (gate) and adapted for callers by
> `AUTH-DEV-AUTH-CALLER-ADAPT-001`.

## What it is

In local/preview runs the platform can mint a development JWT so the frontend and smoke
tests can call authenticated APIs without an OIDC provider. The endpoint is

```
POST /api/dev/auth/token
```

It returns `{ accessToken, tokenType, tenantId, userId, expiresInMs }`.

Because it mints a JWT with `roles=[USER,ADMIN]` for an arbitrary tenant/user, it is gated
by **three** independent conditions:

1. **Property insurance** — `app.security.dev-auth-endpoint=true` (default `false`).
2. **Profile insurance** — the bean is not created under `prod`, `safe-mode` or `oidc`
   (`DevAuthSecretGuard.NOT_PRODUCTION_PROFILES`).
3. **Dev secret** — the request must carry an `X-Dev-Auth-Secret` header that matches
   `app.security.dev-auth-secret` (environment variable `DEV_AUTH_SECRET`). When the secret
   is unset/empty the endpoint **fails closed**: it refuses to mint and answers exactly
   like an unavailable endpoint (401). A missing/wrong secret is never explained.

The `preview` profile turns the property on; production profiles keep it off.

## Setting `DEV_AUTH_SECRET`

Pick one long random value and use it everywhere the backend, the frontend dev server and
the smoke scripts run. Never commit it and never pass it as a command-line argument.

```bash
export DEV_AUTH_SECRET="$(openssl rand -hex 32)"   # once per shell / dev session
```

| Caller | How it gets the secret |
|--------|------------------------|
| Backend (`bootRun` / `preview` profile) | `DEV_AUTH_SECRET` in the process environment (Spring reads `${DEV_AUTH_SECRET:}`) |
| Frontend vite dev server | `DEV_AUTH_SECRET` in the shell that runs `npm run dev`, or in `frontend/.env.local` |
| Smoke scripts | `DEV_AUTH_SECRET` in the shell, or a `.env` file in the repo root / `scripts/smoke/.env` |

The `.env` files are gitignored; see [`.env.example`](../../.env.example) for the placeholder.

> The secret is **not** prefixed with `VITE_`. Vite only exposes `VITE_`-prefixed variables
> to client code, so `DEV_AUTH_SECRET` is never handed to the browser.

## Frontend dev bootstrap

In dev (`import.meta.env.DEV`) the frontend bootstraps a dev JWT lazily on the first API
call (`bootstrapDevAuth` in `frontend/src/api/index.ts`). The browser never holds the
secret; the flow is:

```
browser ──POST /dev-auth/token──▶ vite dev server ──(rewrite + inject X-Dev-Auth-Secret)──▶ POST /api/dev/auth/token
```

* The frontend calls the dev-only path `/dev-auth/token`
  (`frontend/src/api/dev/dev-auth.ts`, `DEV_AUTH_TOKEN_PROXY_PATH`).
* `frontend/vite.config.ts` proxies that path to `/api/dev/auth/token` and attaches the
  `X-Dev-Auth-Secret` header from the Node-side `DEV_AUTH_SECRET` value.
* If `DEV_AUTH_SECRET` is unset the proxy forwards nothing, the backend answers 401, and
  bootstrap silently gives up — no token, no silent success (fail closed).
* Production builds set `import.meta.env.DEV=false`, so the bootstrap never runs and the
  dev-only path is never used. `vite build` with a sentinel secret is checked by
  `scripts/check-dev-auth-bundle-leak.sh`.

## Smoke scripts

The shell smoke tests share `scripts/smoke/lib/dev-auth.sh`:

* `smoke_require_dev_auth_secret` reads `DEV_AUTH_SECRET` from the environment, or from a
  `.env` file (`SMOKE_DEV_AUTH_ENV_FILE`, cwd, repo root, or `scripts/smoke/.env`).
* When the secret is missing it **fails closed** (non-zero) and prints how to set it — it
  never falls back to an unauthenticated call.
* `smoke_dev_auth_token` calls `/api/dev/auth/token` with the `X-Dev-Auth-Secret` header.
  The secret is passed through a curl config on stdin, so it does not appear in `ps`.

Usage:

```bash
export DEV_AUTH_SECRET='...'
RENDER_EXECUTION_WRITE=1 bash scripts/smoke/render-execution-smoke.sh
REAL_MEDIA_RENDER_WRITE=1 bash scripts/smoke/real-media-render-smoke.sh
```

## Verifying the setup

```bash
# 1. Smoke dev-auth caller: fail-closed + header carriage (hermetic, no backend needed)
bash scripts/smoke/dev-auth-caller.test.sh

# 2. Frontend dev bootstrap end-to-end (real vite dev server + stub backend)
bash scripts/smoke/dev-auth-bootstrap-e2e.sh

# 3. No secret in the production bundle
bash scripts/check-dev-auth-bundle-leak.sh
```

## Production

* `prod` / `safe-mode` / `oidc` never register the dev-auth surface.
* `app.security.dev-auth-endpoint` is `false` in `application-prod.yml`, the `k8s`
  overlays and the production gitops manifests.
* `DEV_AUTH_SECRET` is a local-development convenience. It must never be provided to a
  production deployment, and the secret is never embedded in the frontend bundle.
