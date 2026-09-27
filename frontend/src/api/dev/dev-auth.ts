/**
 * Dev-auth bootstrap transport (AUTH-DEV-AUTH-CALLER-ADAPT-001).
 *
 * The backend endpoint `/api/dev/auth/token` requires the `X-Dev-Auth-Secret`
 * header (see `DevAuthSecretGuard`). The browser must never hold that secret, so
 * the frontend does not call the backend path directly.
 *
 * Instead the vite dev server exposes the dev-only path below and, at request
 * time, rewrites it to `/api/dev/auth/token` and injects the secret header from
 * the Node-side `DEV_AUTH_SECRET` environment variable (see `vite.config.ts`).
 *
 * This path only exists behind the vite dev server. Every caller is gated by
 * `import.meta.env.DEV`, so a production build never reaches it and never
 * contains the secret.
 */
export const DEV_AUTH_TOKEN_PROXY_PATH = '/dev-auth/token'
