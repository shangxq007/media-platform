import { UserManager, WebStorageStateStore, type User } from 'oidc-client-ts'
import { getOidcSettings, isOidcEnabled } from './oidcConfig'

let userManager: UserManager | null = null
// Initiation has no native SDK event; retire local consumers before redirect work.
const signoutListeners = new Set<() => void>()

function manager(): UserManager {
  if (!isOidcEnabled()) {
    throw new Error('OIDC is not configured (set VITE_OIDC_ISSUER)')
  }
  if (!userManager) {
    const { issuer, clientId, redirectUri, scope } = getOidcSettings()
    userManager = new UserManager({
      authority: issuer,
      client_id: clientId,
      redirect_uri: redirectUri,
      response_type: 'code',
      scope,
      automaticSilentRenew: true,
      loadUserInfo: true,
      userStore: new WebStorageStateStore({ store: window.sessionStorage }),
    })
  }
  return userManager
}

// sid is optional at the IdP, but without it this consumer cannot prove continuity.
// This is a draft-retirement comparison, never a permission or authentication grant.
function sessionIdentity(user: User | null | undefined): string | null {
  if (!user) return null
  const { profile } = user
  const text = (value: unknown): value is string => typeof value === 'string' && Boolean(value.trim())
  if (!text(profile.iss) || !text(profile.sub) || !text(profile.sid)) return null
  const audience = typeof profile.aud === 'string' ? [profile.aud] : profile.aud
  if (!Array.isArray(audience) || !audience.every(text) || !audience.includes(getOidcSettings().clientId)) return null
  const { tenantId, tenant_id } = profile
  if ((tenantId !== undefined && !text(tenantId)) || (tenant_id !== undefined && !text(tenant_id))
    || (tenantId !== undefined && tenant_id !== undefined && tenantId !== tenant_id)) return null
  return JSON.stringify([getOidcSettings().issuer, profile.iss, [...new Set(audience)].sort(), profile.sub, profile.sid, tenantId ?? tenant_id ?? null])
}

function sessionContinuity(user: User | null | undefined): string | null {
  const identity = sessionIdentity(user)
  if (!identity || !user || user.expired !== false || !Number.isFinite(user.expires_at) || !user.access_token) return null
  return JSON.stringify([identity, [...new Set(user.scopes)].sort()])
}

/** Observe SDK identity without treating a normal token renewal as signout. */
export function subscribeOidcSessionRetirement(onRetire: () => void): () => void {
  if (!isOidcEnabled()) return () => {}
  const sdk = manager(), { events } = sdk
  let active = true
  let revision = 0
  let initialized = false
  let continuity: string | null = null
  const retire = () => {
    if (!active) return
    revision += 1
    initialized = true
    continuity = null
    onRetire()
  }
  const observe = (user: User | null) => {
    const next = sessionContinuity(user)
    const changed = !next || (initialized && next !== continuity)
    initialized = true
    continuity = next
    if (changed) onRetire()
  }
  const loaded = async () => {
    if (!active) return
    const generation = ++revision
    try {
      // SDK stores before UserLoaded. Read current identity, not the possibly old
      // payload: even an old notification can supersede a pending genuine change.
      // getUser defaults to raiseEvent=false, so this cannot recurse into loaded.
      const current = await sdk.getUser()
      if (!active || revision !== generation) return
      observe(current)
    } catch {
      if (active && revision === generation) retire()
    }
  }
  events.addUserLoaded(loaded)
  events.addUserUnloaded(retire)
  events.addAccessTokenExpired(retire)
  events.addUserSignedIn(retire)
  events.addUserSignedOut(retire)
  events.addUserSessionChanged(retire)
  signoutListeners.add(retire)
  // Subscribe first. Any intervening event supersedes this initial hydration.
  const generation = revision
  void sdk.getUser().then(user => {
    if (active && revision === generation) observe(user)
  }, () => {
    if (active && revision === generation) retire()
  })
  return () => {
    active = false
    revision += 1
    events.removeUserLoaded(loaded)
    events.removeUserUnloaded(retire)
    events.removeAccessTokenExpired(retire)
    events.removeUserSignedIn(retire)
    events.removeUserSignedOut(retire)
    events.removeUserSessionChanged(retire)
    signoutListeners.delete(retire)
  }
}

export async function signInRedirect(): Promise<void> {
  await manager().signinRedirect()
}

export async function handleOAuthCallback(): Promise<User> {
  const user = await manager().signinRedirectCallback()
  if (user.profile?.sub) {
    localStorage.setItem('user_id', user.profile.sub)
  }
  const tenant =
    (user.profile as Record<string, unknown>)?.tenantId ??
    (user.profile as Record<string, unknown>)?.tenant_id
  if (typeof tenant === 'string' && tenant) {
    localStorage.setItem('tenant_id', tenant)
  }
  return user
}

export async function getOidcUser(): Promise<User | null> {
  if (!isOidcEnabled()) return null
  try {
    return await manager().getUser()
  } catch {
    return null
  }
}

export async function getAccessToken(): Promise<string | null> {
  const user = await getOidcUser()
  if (!user || user.expired) return null
  return user.access_token ?? null
}

export async function signOutOidc(): Promise<void> {
  if (!isOidcEnabled()) return
  for (const retire of signoutListeners) retire()
  await manager().signoutRedirect()
}
