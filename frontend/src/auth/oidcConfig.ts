/** OIDC (Authentik) — enabled when VITE_OIDC_ISSUER is set at build time. */

export function isOidcEnabled(): boolean {
  const issuer = import.meta.env.VITE_OIDC_ISSUER as string | undefined
  return Boolean(issuer && issuer.trim().length > 0)
}

export function getOidcSettings() {
  const issuer = (import.meta.env.VITE_OIDC_ISSUER as string).replace(/\/?$/, '/')
  const clientId = import.meta.env.VITE_OIDC_CLIENT_ID as string
  const redirectUri =
    (import.meta.env.VITE_OIDC_REDIRECT_URI as string) ||
    `${window.location.origin}/oauth/callback`
  const scope = (import.meta.env.VITE_OIDC_SCOPE as string) || 'openid profile email'
  return { issuer, clientId, redirectUri, scope }
}
