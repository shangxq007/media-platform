import { AxiosError, type InternalAxiosRequestConfig } from 'axios'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'

const sdk = vi.hoisted(() => ({
  user: null as unknown,
  getUser: vi.fn<() => Promise<unknown>>(),
  redirect: vi.fn<() => Promise<void>>(),
  callback: vi.fn<() => Promise<unknown>>(),
  signout: vi.fn<() => Promise<void>>(),
  events: new Map<string, Set<(...args: unknown[]) => unknown>>(),
}))
vi.mock('../../auth/oidcConfig', () => ({ isOidcEnabled: () => true, getOidcSettings: () => ({
  issuer: 'https://issuer.test', clientId: 'frontend-test', redirectUri: 'http://localhost/oauth/callback', scope: 'openid profile',
}) }))
vi.mock('oidc-client-ts', () => {
  const events = Object.fromEntries(['UserLoaded', 'UserUnloaded', 'AccessTokenExpired', 'UserSignedIn', 'UserSignedOut', 'UserSessionChanged'].flatMap(name => {
    const listeners = new Set<(...args: unknown[]) => unknown>(); sdk.events.set(name, listeners)
    return [[`add${name}`, (listener: (...args: unknown[]) => unknown) => listeners.add(listener)], [`remove${name}`, (listener: (...args: unknown[]) => unknown) => listeners.delete(listener)]]
  }))
  return { WebStorageStateStore: class {}, UserManager: class {
    events = events
    getUser() { return sdk.getUser() }
    signinRedirect() { return sdk.redirect() }
    signinRedirectCallback() { return sdk.callback() }
    signoutRedirect() { return sdk.signout() }
  } }
})
import api from '../../api'
import { handleOAuthCallback, signOutOidc, subscribeOidcSessionRetirement } from '../../auth/oidcClient'

let sequence = 0
const originalAdapter = api.defaults.adapter
const subscriptions: (() => void)[] = []
function user(key: string) { return { access_token: `token-${key}`, expired: false, expires_at: Date.now() / 1000 + 3600, scopes: ['openid'],
  profile: { iss: 'https://issuer.test', aud: 'frontend-test', sub: `user-${key}`, sid: `sid-${key}`, tenantId: `tenant-${key}` } } }
async function emit(name: string) { for (const listener of [...sdk.events.get(name) ?? []]) await listener(sdk.user) }
async function watch() { const retired = vi.fn(); subscriptions.push(subscribeOidcSessionRetirement(retired)); await Promise.resolve(); await Promise.resolve(); return retired }
function failed(config: InternalAxiosRequestConfig, status = 401) { return new AxiosError('Read rejected', undefined, config, undefined, { status, statusText: 'Rejected', headers: {}, config, data: {} }) }
function pendingRequests() {
  const requests: { config: InternalAxiosRequestConfig; reject: (reason: unknown) => void }[] = []
  api.defaults.adapter = config => new Promise((_, reject) => { requests.push({ config, reject }) })
  return requests
}
beforeEach(async () => {
  sdk.user = user(String(++sequence)); sdk.getUser.mockReset().mockImplementation(async () => sdk.user)
  sdk.callback.mockReset().mockImplementation(async () => sdk.user); sdk.redirect.mockReset().mockResolvedValue(undefined); sdk.signout.mockReset().mockResolvedValue(undefined)
  vi.spyOn(console, 'error').mockImplementation(() => {}); await handleOAuthCallback()
})
afterEach(() => { subscriptions.splice(0).forEach(stop => stop()); api.defaults.adapter = originalAdapter; vi.restoreAllMocks(); sessionStorage.clear(); localStorage.clear() })

it('stale OIDC 401 cannot retire or redirect a newer authenticated session', async () => {
  const requests = pendingRequests(); const pending = api.get('/old').catch(error => error)
  await vi.waitFor(() => expect(requests).toHaveLength(1))
  sdk.user = user(`replacement-${sequence}`); await emit('UserLoaded'); const retired = await watch()
  requests[0].reject(failed(requests[0].config)); expect((await pending).response.status).toBe(401)
  expect(retired).not.toHaveBeenCalled(); expect(sdk.redirect).not.toHaveBeenCalled()
})
it('concurrent OIDC 401 responses retire once and do not create redirect loops', async () => {
  const retired = await watch(); const requests = pendingRequests()
  const first = api.get('/first').catch(error => error), second = api.get('/second').catch(error => error)
  await vi.waitFor(() => expect(requests).toHaveLength(2)); requests.forEach(request => request.reject(failed(request.config)))
  await Promise.all([first, second]); expect(retired).toHaveBeenCalledOnce(); expect(sdk.redirect).toHaveBeenCalledOnce()
})
it('retires immediately before a pending redirect, then allows a legitimate new authentication', async () => {
  const retired = await watch(); let finish!: () => void
  sdk.redirect.mockImplementation(() => new Promise<void>(resolve => { finish = resolve }))
  api.defaults.adapter = async config => { throw failed(config) }
  const pending = api.get('/expired').catch(error => error)
  await vi.waitFor(() => expect(sdk.redirect).toHaveBeenCalledOnce()); expect(retired).toHaveBeenCalledOnce()
  finish(); expect((await pending).response.status).toBe(401)
  sdk.user = user(`new-login-${sequence}`); await handleOAuthCallback(); await emit('UserLoaded')
  api.defaults.adapter = async config => ({ status: 200, statusText: 'OK', headers: {}, config, data: { usable: true } })
  expect((await api.get('/new')).data).toEqual({ usable: true }); expect(sdk.redirect).toHaveBeenCalledOnce()
})
it('old-token 401 during same-session renewal does not retire valid renewed credentials', async () => {
  const original = user(`renew-${sequence}`); sdk.user = original; await handleOAuthCallback(); const retired = await watch()
  const requests = pendingRequests(); const pending = api.get('/renew').catch(error => error)
  await vi.waitFor(() => expect(requests).toHaveLength(1)); sdk.user = { ...original, access_token: 'renewed-token' }; await emit('UserLoaded')
  requests[0].reject(failed(requests[0].config)); await pending
  expect(retired).not.toHaveBeenCalled(); expect(sdk.redirect).not.toHaveBeenCalled()
})
it('abort during the asynchronous current-session check causes no retirement or redirect', async () => {
  const retired = await watch(); const controller = new AbortController(); const requests = pendingRequests()
  const pending = api.get('/aborted', { signal: controller.signal }).catch(error => error)
  await vi.waitFor(() => expect(requests).toHaveLength(1)); let finish!: (value: unknown) => void; let checking = false
  sdk.getUser.mockImplementationOnce(() => { checking = true; return new Promise(resolve => { finish = resolve }) })
  requests[0].reject(failed(requests[0].config)); await vi.waitFor(() => expect(checking).toBe(true)); controller.abort(); finish(sdk.user); await pending
  expect(retired).not.toHaveBeenCalled(); expect(sdk.redirect).not.toHaveBeenCalled()
})
it.each([403, 500])('HTTP %s is not authentication expiry', async status => {
  const retired = await watch(); api.defaults.adapter = async config => { throw failed(config, status) }
  await expect(api.get('/failure')).rejects.toMatchObject({ response: { status } })
  expect(retired).not.toHaveBeenCalled(); expect(sdk.redirect).not.toHaveBeenCalled()
})
it('a network failure is not authentication expiry', async () => {
  const retired = await watch(); api.defaults.adapter = async () => { throw new AxiosError('Network Error') }
  await expect(api.get('/offline')).rejects.toThrow('Network Error'); expect(retired).not.toHaveBeenCalled(); expect(sdk.redirect).not.toHaveBeenCalled()
})
it('explicit signout retirement does not start an automatic signin from an old request', async () => {
  const requests = pendingRequests(); const pending = api.get('/before-signout').catch(error => error)
  await vi.waitFor(() => expect(requests).toHaveLength(1)); await signOutOidc(); requests[0].reject(failed(requests[0].config)); await pending
  expect(sdk.signout).toHaveBeenCalledOnce(); expect(sdk.redirect).not.toHaveBeenCalled()
})
