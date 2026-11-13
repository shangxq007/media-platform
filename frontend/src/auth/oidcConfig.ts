/**
 * OIDC (Authentik) — enabled when an issuer is configured.
 *
 * Configuration is resolved in two layers:
 * 1. Runtime: `window.__MEDIA_PLATFORM_CONFIG__.oidc`, served by the backend `/config.js`
 *    endpoint from the deployment environment (`app.security.oauth2.issuer-uri` /
 *    `audience`). This keeps one reproducible, environment-independent bundle.
 * 2. Build-time fallback: `VITE_OIDC_*`, used by the Vite dev server and by a production
 *    build that is not served by this backend.
 *
 * No credential, secret, or token is read here; only the issuer, client id and redirect
 * origin needed to start an Authorization Code + PKCE flow.
 */

export interface OidcRuntimeConfig {
  enabled?: boolean
  issuer?: string
  clientId?: string
  redirectUri?: string
  scope?: string
}

interface MediaPlatformRuntimeConfig {
  oidc?: OidcRuntimeConfig
}

declare global {
  interface Window {
    __MEDIA_PLATFORM_CONFIG__?: MediaPlatformRuntimeConfig
  }
}

function runtimeOidc(): OidcRuntimeConfig {
  if (typeof window === 'undefined') return {}
  return window.__MEDIA_PLATFORM_CONFIG__?.oidc ?? {}
}

export function isOidcEnabled(): boolean {
  const issuer = runtimeOidc().issuer ?? (import.meta.env.VITE_OIDC_ISSUER as string | undefined)
  return Boolean(issuer && issuer.trim().length > 0)
}

export function getOidcSettings() {
  const runtime = runtimeOidc()
  const issuer = (runtime.issuer ?? (import.meta.env.VITE_OIDC_ISSUER as string))
    .replace(/\/?$/, '/')
  const clientId = runtime.clientId ?? (import.meta.env.VITE_OIDC_CLIENT_ID as string)
  const redirectUri =
    runtime.redirectUri ||
    (import.meta.env.VITE_OIDC_REDIRECT_URI as string) ||
    `${window.location.origin}/oauth/callback`
  const scope = runtime.scope || (import.meta.env.VITE_OIDC_SCOPE as string) || 'openid profile email'
  return { issuer, clientId, redirectUri, scope }
}
